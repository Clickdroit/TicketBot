## Plan: Sakura V2 full stack + exécution + post-livraison

### Objectif
Livrer Sakura V2 de manière incrémentale et sûre, puis enchaîner sur une phase de stabilisation stricte pour corriger le moindre bug et la moindre incohérence (fonctionnelle, UX, logs, data).

### Périmètre V2
1. Architecture data SQLite robuste
2. Auto-mod + sanctions automatiques
3. XP / leveling
4. Tickets professionnels
5. Utilitaires staff (`/lock`, `/unlock`, `/slowmode`, `/say`, ...)
6. Extras V2+ (observabilité, anti-abus avancé, qualité de vie modération)

---

## Plan d’exécution détaillé (implémentation)

### Phase 0 — Cadrage & sécurité de livraison
- Geler les exigences fonctionnelles et permissions Discord par feature.
- Définir des critères d’acceptation mesurables (DoD) pour chaque module.
- Préparer stratégie release: feature flags, rollback, migrations réversibles.
- Vérifier que les commandes existantes restent compatibles (mono-guild, modération actuelle, config actuelle).

### Phase 1 — Data layer SQLite robuste
- Introduire une table `schema_migrations` et des migrations versionnées idempotentes.
- Renforcer schéma:
  - index sur lectures fréquentes,
  - contraintes d’intégrité (`NOT NULL`, `CHECK`, `UNIQUE` selon besoin),
  - colonnes d’audit (`created_at`, `updated_at`).
- Uniformiser l’accès DB via repositories/services (transactions explicites, gestion d’erreurs homogène).
- Ajouter sauvegarde/restauration opérable (backup fichier SQLite + procédure de restore testée).

### Phase 2 — Moteur auto-mod & sanctions automatiques
- Transformer la logique actuelle en moteur de règles configurable (liens, spam, mentions, caps, mots interdits, répétitions).
- Créer une politique graduelle de sanctions automatiques:
  - avertissement,
  - suppression message,
  - timeout automatique,
  - escalade selon historique utilisateur.
- Journaliser chaque décision auto-mod avec raison explicite, règle déclenchée, durée et contexte.
- Ajouter exemptions sécurisées (rôles/salons whitelistés) + anti-faux positifs.

### Phase 3 — XP / leveling
- Définir formule XP, cooldown anti-farm, seuils de niveau.
- Gérer attributions de rôles de niveau (mapping configurable).
- Ajouter commandes de consultation/ranking/reset staff.
- Protéger contre abus (spam de messages courts, floods, commandes auto).

### Phase 4 — Tickets pro
- Flux complet: création via panel/bouton, catégorisation, assignation staff, transcript, fermeture/archivage.
- Gérer permissions fines par salon ticket (auteur, staff, rôles support).
- Ajouter SLA interne: statuts, timestamps, responsables, raison de fermeture.
- Prévoir mécanisme anti-duplication de tickets par utilisateur.

### Phase 5 — Utilitaires staff
- Implémenter `/lock`, `/unlock`, `/slowmode`, `/say`, et commandes associées utiles à la modération quotidienne.
- Uniformiser UX réponses slash (messages succès/erreur cohérents, éphemeral quand pertinent).
- Vérifier permissions Discord exactes avant action; refus explicite sinon.
- Centraliser logs d’actions staff avec format homogène.

### Phase 6 — Extras V2+
- Observabilité: métriques clés (infractions, sanctions, tickets ouverts/fermés, latence commandes).
- Outils qualité de vie: presets de config, exports modération, diagnostics d’état.
- Durcissement anti-abus: limites de fréquence, contrôles de concurrence, protections anti-race conditions.

---

## Plan "après fin" (stabilisation / QA / hardening)

### 1) Freeze fonctionnel + triage
- Stopper les nouvelles features pendant la stabilisation.
- Ouvrir un backlog dédié "bug/incohérence" priorisé par sévérité (critique, majeur, mineur).
- Reproduire systématiquement chaque défaut avant correction.

### 2) Chasse aux bugs systématique
- Parcourir tous les flux critiques:
  - modération manuelle,
  - auto-mod,
  - sanctions automatiques,
  - XP/levels,
  - tickets,
  - utilitaires staff,
  - startup/reconnect bot.
- Corriger en priorité:
  - corruptions/erreurs DB,
  - permissions incorrectes,
  - commandes incohérentes,
  - logs incomplets,
  - faux positifs auto-mod.

### 3) Résolution des incohérences globales
- Cohérence fonctionnelle: même règle métier quel que soit le point d’entrée.
- Cohérence UX: wording, embeds, erreurs, confirmations, ephemerals/public.
- Cohérence data: formats dates/IDs/raisons, nettoyage des valeurs invalides.
- Cohérence logs/audit: événement traçable de bout en bout.

### 4) Stratégie de tests complète
- Unitaires:
  - règles auto-mod,
  - escalade sanctions,
  - calcul XP/levels,
  - services tickets,
  - validation permissions.
- Intégration:
  - DB SQLite réelle (migrations, transactions, rollback),
  - enchaînement commande -> service -> DB -> logs.
- E2E (scénarios guild):
  - parcours modération complet,
  - création/résolution ticket,
  - montée de niveau,
  - incidents réseau/restart.
- Non-régression: rejouer cas historiques ayant déjà cassé.

### 5) Validation sécurité, permissions, concurrence, charge
- Permissions Discord: vérifier chaque commande et action auto contre les permissions requises.
- Concurrence: tests sur accès simultanés (sanctions, tickets, XP updates) pour éviter doubles écritures.
- Charge: simulation rafales de messages/infractions pour vérifier latence et stabilité.
- Sécurité: validation des entrées utilisateur, limites de taille, neutralisation contenus dangereux pour logs/embeds.

### 6) Validation migrations DB & robustesse opérationnelle
- Tester migration depuis état production existant vers V2 sur copie de données.
- Vérifier intégrité post-migration + compatibilité arrière si rollback nécessaire.
- Documenter et tester procédure backup/restore de bout en bout.

### 7) Observabilité & exploitation
- Compléter logs structurés (niveau, contexte, corrélation).
- Définir indicateurs de santé et seuils d’alerte.
- Préparer runbook d’incident (diagnostic, contournement, escalation).

### 8) Release contrôlée + rollback
- Déploiement progressif avec checklist Go/No-Go.
- Fenêtre de monitoring renforcée post-release.
- Critères de rollback explicites (erreur DB, taux d’échec commandes, régressions critiques).
- Exécuter rollback immédiatement si seuil critique dépassé.

### 9) Clôture qualité
- Aucun bug critique/ou majeur ouvert.
- Incohérences principales traitées et validées.
- Tests passants + check manuel final.
- Documentation à jour (fonctionnel, QA, release, rollback).
