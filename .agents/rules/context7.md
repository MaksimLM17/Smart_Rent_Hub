---
description: Rule for using context7 skill
trigger: always_on
---

# Context7 Skill Usage Rule

When using the `context7` skill to search for or read documentation, ALWAYS use Git Bash (or a Bash-compatible shell) to execute the scripts (e.g., `bash .agents/skills/context7/scripts/context7.sh`). Do not try to run it via pure Windows PowerShell without wrapping it in `bash`.
