$gh = "C:\Program Files\GitHub CLI\gh.exe"
$repo = "MaksimLM17/Y_LAB_Homework"

Write-Host "Creating issues for Stage 0 (E0 and S-1)..."

& $gh issue create --repo $repo --title "[T0.1] Инициализировать монорепо по SDD 3.5" --body "Инициализировать монорепо по SDD 3.5: каталоги contracts, libs, services, tools, web, infra, load-tests, docs; .gitignore, .editorconfig, .gitattributes, README. Структура в main, README ведёт к docs/." --label "task"
& $gh issue create --repo $repo --title "[T0.2] Maven multi-module" --body "Maven multi-module: родительский POM и BOM версий (Java 21, Spring Boot 4.1.x, Operaton, Spring AI, Testcontainers), Maven Wrapper." --label "task"
& $gh issue create --repo $repo --title "[T0.3] Настроить GitHub" --body "Настроить GitHub: защита main, шаблоны Issue (task, spike) и PR с чек-листом, labels, milestone «Этап 0», Project board." --label "task"
& $gh issue create --repo $repo --title "[T0.4] CI ci: сборка и тесты" --body "CI ci: сборка и тесты (кэш Maven, фильтры по путям), запуск ArchUnit. PR без зелёного CI не вливается." --label "task"
& $gh issue create --repo $repo --title "[T0.5] Безопасность цепочки поставок" --body "Безопасность цепочки поставок: Dependabot (Maven, npm, Docker), CodeQL, secret scanning. Конфигурации в .github/, первый прогон успешен." --label "task"
& $gh issue create --repo $repo --title "[T0.6] Форматирование кода" --body "Форматирование кода (Spotless + google-java-format) и проверка в CI. Нарушение форматирования ломает сборку." --label "task"
& $gh issue create --repo $repo --title "[S-1] Spike: Spring Boot 4.1 + Operaton" --body "Вопрос: работает ли Operaton на Boot 4.1.x и сохраняет ли атомарность с JPA. Шаги описаны в Stage0_Plan.md." --label "spike"

Write-Host "Done."
