#!/bin/sh
set -e

export PGUSER="${POSTGRES_USER:-postgres}"

echo ">>> [SmartRentHub] Initializing PostgreSQL cluster..."

# 1. System role: debezium (with REPLICATION)
echo ">>> [SmartRentHub] Creating debezium replication role..."
psql -v ON_ERROR_STOP=1 <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'debezium') THEN
            CREATE ROLE debezium WITH LOGIN REPLICATION PASSWORD '${DEBEZIUM_DB_PASSWORD:-debezium_pass}';
        END IF;
    END
    \$\$;
EOSQL

# 2. System service: keycloak
echo ">>> [SmartRentHub] Initializing keycloak role and database..."
psql -v ON_ERROR_STOP=1 <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'keycloak') THEN
            CREATE ROLE keycloak WITH LOGIN PASSWORD '${KEYCLOAK_DB_PASSWORD:-keycloak_pass}';
        END IF;
    END
    \$\$;
EOSQL

if ! psql -v ON_ERROR_STOP=1 -tc "SELECT 1 FROM pg_database WHERE datname = 'keycloak'" | grep -q 1; then
    psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE keycloak OWNER keycloak;"
fi

psql -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;"

# 3. System service: litellm
echo ">>> [SmartRentHub] Initializing litellm role and database..."
psql -v ON_ERROR_STOP=1 <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'litellm') THEN
            CREATE ROLE litellm WITH LOGIN PASSWORD '${LITELLM_DB_PASSWORD:-litellm_pass}';
        END IF;
    END
    \$\$;
EOSQL

if ! psql -v ON_ERROR_STOP=1 -tc "SELECT 1 FROM pg_database WHERE datname = 'litellm'" | grep -q 1; then
    psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE litellm OWNER litellm;"
fi

psql -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON DATABASE litellm TO litellm;"

# 4. Business services: booking, inventory, payments, risk, notification, ai, finance
SERVICES="booking inventory payments risk notification ai finance"

for service in $SERVICES; do
    echo ">>> [SmartRentHub] Initializing service database: ${service}..."

    owner_role="${service}_owner"
    app_role="${service}_app"
    owner_pass="${service}_owner_pass"
    app_pass="${service}_app_pass"

    # Create owner (DDL/migration) and app (DML runtime) roles
    psql -v ON_ERROR_STOP=1 <<-EOSQL
        DO \$\$
        BEGIN
            IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${owner_role}') THEN
                CREATE ROLE ${owner_role} WITH LOGIN PASSWORD '${owner_pass}';
            END IF;
            IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${app_role}') THEN
                CREATE ROLE ${app_role} WITH LOGIN PASSWORD '${app_pass}';
            END IF;
        END
        \$\$;
EOSQL

    # Create database owned by owner_role if not exists
    if ! psql -v ON_ERROR_STOP=1 -tc "SELECT 1 FROM pg_database WHERE datname = '${service}'" | grep -q 1; then
        psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE ${service} OWNER ${owner_role};"
    fi

    # Database-level grants
    psql -v ON_ERROR_STOP=1 <<-EOSQL
        GRANT ALL PRIVILEGES ON DATABASE ${service} TO ${owner_role};
        REVOKE ALL ON DATABASE ${service} FROM PUBLIC;
        GRANT CONNECT ON DATABASE ${service} TO ${app_role};
        GRANT CONNECT ON DATABASE ${service} TO debezium;
        GRANT CREATE ON DATABASE ${service} TO debezium;
EOSQL

    # Schema-level grants & privileges inside service database
    psql -v ON_ERROR_STOP=1 --dbname "${service}" <<-EOSQL
        -- Secure public schema
        REVOKE CREATE ON SCHEMA public FROM PUBLIC;
        ALTER SCHEMA public OWNER TO ${owner_role};
        GRANT ALL ON SCHEMA public TO ${owner_role};

        -- Grant runtime DML privileges to application user
        GRANT USAGE ON SCHEMA public TO ${app_role};
        REVOKE CREATE ON SCHEMA public FROM ${app_role};
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${app_role};
        GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO ${app_role};
        GRANT EXECUTE ON ALL ROUTINES IN SCHEMA public TO ${app_role};

        -- Default privileges for future tables/sequences/routines created by owner
        ALTER DEFAULT PRIVILEGES FOR ROLE ${owner_role} IN SCHEMA public
            GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_role};
        ALTER DEFAULT PRIVILEGES FOR ROLE ${owner_role} IN SCHEMA public
            GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO ${app_role};
        ALTER DEFAULT PRIVILEGES FOR ROLE ${owner_role} IN SCHEMA public
            GRANT EXECUTE ON ROUTINES TO ${app_role};

        -- Debezium privileges for CDC logical decoding & reading outbox
        GRANT USAGE ON SCHEMA public TO debezium;
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO debezium;
        ALTER DEFAULT PRIVILEGES FOR ROLE ${owner_role} IN SCHEMA public
            GRANT SELECT ON TABLES TO debezium;

        -- Pre-create bpm schema for Operaton process engine
        CREATE SCHEMA IF NOT EXISTS bpm AUTHORIZATION ${owner_role};
        GRANT USAGE ON SCHEMA bpm TO ${app_role};
        REVOKE CREATE ON SCHEMA bpm FROM ${app_role};
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA bpm TO ${app_role};
        GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA bpm TO ${app_role};
        GRANT EXECUTE ON ALL ROUTINES IN SCHEMA bpm TO ${app_role};

        ALTER DEFAULT PRIVILEGES FOR ROLE ${owner_role} IN SCHEMA bpm
            GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_role};
        ALTER DEFAULT PRIVILEGES FOR ROLE ${owner_role} IN SCHEMA bpm
            GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO ${app_role};
        ALTER DEFAULT PRIVILEGES FOR ROLE ${owner_role} IN SCHEMA bpm
            GRANT EXECUTE ON ROUTINES TO ${app_role};
EOSQL
done

echo ">>> [SmartRentHub] PostgreSQL initialization completed successfully."
