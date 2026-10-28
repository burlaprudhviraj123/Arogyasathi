# Arogyasathi Project Rules — Mandatory Engineering Guidelines

## 1. 🧠 Context must never be lost
- Maintain a living `PROJECT_MANIFEST.md` containing the project's purpose, decisions, current phase, completed work, pending work, and constraints.
- Read existing documentation and inspect the repository before making changes.
- Never silently discard previous decisions or redesign something without justification.
- Keep the original architecture, particularly JEV and Laya, and audit it before proposing replacements.
- Separate verified facts, unverified claims, assumptions, and recommendations.

## 2. 🔬 Research before implementation
- Thoroughly investigate every provided reference, including source code, tests, architecture, and implementation details where available.
- Create a separate Markdown research document for each source.
- Don't treat a README's claims as verified facts.
- Compare the references and explain what Arogyasathi should adopt, modify, or avoid.
- Support important architectural decisions with evidence.
- Distinguish the hackathon's actual requirements from optional features and personal recommendations.

## 3. 🚦 Strict, phase-gated workflow
Follow the agreed sequence:
1. Understand the problem statement and document its constraints.
2. Audit the original architecture.
3. Research the individual references.
4. Produce the comparative analysis.
5. Create one authoritative, complete system architecture for the team.
6. Create a separate architecture document specifically for your offline Android application.
7. Review and approve the architecture.
8. Only then implement the core engine and validate it against the actual evaluator.
9. Build the Android application after the core implementation has passed its review.

Do not jump ahead. Do not start coding before authorization. Do not automatically proceed to the next phase just because the current phase is finished.

## 4. ⚡ Performance is an engineering requirement
You don't want an application that merely works in a demo. You want actual measurements of:
- APK and model size.
- RAM consumption and peak memory.
- CPU usage and inference latency.
- Cold-start and response times.
- Offline functionality and reliability.
- Synchronization performance when connectivity returns.

No invented benchmark results. No claims such as "zero OOM guaranteed" without evidence. Model selection must consider the actual target devices, runtime compatibility, and measured resource requirements.

## 5. 🎯 Build what the problem requires — not unnecessary features
For Arogyasathi:
- Deterministic danger-sign detection must run before AI-generated reasoning.
- Protocol-based answers must include valid citations.
- Out-of-scope medical questions must be refused safely and redirected appropriately.
- Offline inference must be real, not a UI that merely claims to support offline mode.
- Household management and prioritized visits must follow the supplied data and rules.
- The actual evaluator and its datasets are the source of truth for hackathon compliance.

Never hardcode answers just to pass tests, modify the evaluator to manufacture success, invent clinical rules, or fabricate results.

## 6. 📱 UI must be minimal and purposeful
- No unnecessary cards, buttons, menus, dashboards, or redundant options.
- No promotional banners or decorative AI features that don't help frontline workers.
- Prioritize clear workflows, accessibility, and fast interactions on low-end devices.
- Every interface element must have a practical purpose.
- Don't add features simply to make the application look more impressive.

## 7. 📄 Documentation is part of the deliverable
Maintain the project manifest, architecture documents, research documents, architecture decisions, and requirement traceability.

Keep the documentation consistent with the actual code. Update it when an approved decision changes. Make it possible for a new AI session or a team member to understand the project's state without relying on conversation history.

## 8. 🛑 No lazy completion claims
Before reporting a task as complete, the AI must:
- Inspect what already exists.
- Implement the requested work within the approved scope.
- Run relevant tests and the actual evaluator where applicable.
- Report what passed, what failed, and what remains unverified.
- Provide evidence for performance claims.
- Stop at the agreed phase gate and wait for your review.
