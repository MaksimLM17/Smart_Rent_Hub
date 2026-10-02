---
description: Git branching workflow and commit conventions for BMAD methodology
trigger: always_on
---

# Git Workflow Rule

When starting work on any task (e.g., T0.1, S-1), NEVER commit directly to the `main` branch.
1. ALWAYS create and checkout a new branch specific to the task (e.g., `git checkout -b feature/T0.1-init-monorepo`).
2. Implement the task in this branch.
3. Commit using **Conventional Commits** format (e.g., `feat: [T0.1] add monorepo structure`, `chore: update .gitignore`, `fix: resolve conflict`).
4. Push the branch to the remote repository.
5. STOP. Do not create or merge the Pull Request yourself. Notify the user that the branch is ready. The creation and merging of Pull Requests is ALWAYS handled by the HUMAN user.
