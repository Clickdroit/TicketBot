# 🛡️ Guide du module Protect (Sakura)

Ce document explique **comment fonctionne Protect** et **comment le configurer correctement** pour limiter les faux positifs.

## 1) Ce que fait Protect

Protect est composé de 3 briques actives :

- **Anti-Bot / Anti-Raid Join** (`JoinProtectionListener`)
  - calcule un **score de risque** à l’arrivée d’un membre selon :
    - âge du compte,
    - burst de joins récents,
    - état d’un raid mode temporaire.
  - applique une action progressive :
    - risque modéré → quarantaine (si rôle configuré),
    - risque critique → quarantaine, sinon kick.

- **Anti-Vandalisme** (`AntiVandalismListener`)
  - surveille les actions sensibles (création/suppression salon, rôles, permissions, ban/kick),
  - corrèle les audit logs de façon stricte (action + cible + fenêtre de temps),
  - applique des sanctions progressives : **warn → timeout → ban**.

- **Anti-Phishing** (`PhishingProtectionListener` + `PhishingService`)
  - parse uniquement les URLs du message,
  - normalise le hostname (incluant IDN/punycode),
  - compare domaine exact/sous-domaine à une blocklist,
  - gère une allowlist de domaines sûrs,
  - résout prudemment certains raccourcisseurs.

## 2) Exclusions de sécurité intégrées

Sur l’anti-vandalisme, Protect ignore les acteurs de confiance :

- owner du serveur,
- bots,
- membres avec permissions staff (`ADMINISTRATOR`/`MANAGE_SERVER`),
- utilisateurs en whitelist,
- rôles staff marqués “trusted”.

## 3) Commande `/protect` (admin)

La commande nécessite la permission admin.

### Sous-commandes principales

- `/protect module type:<antibot|antiraid|antiphishing> etat:<true|false>`
- `/protect accountage heures:<int>`
- `/protect raidconfig seuil:<int>=3+ fenetre:<int>=10+ duree:<int>=30+`
- `/protect quarantine [role:<role>]`  
  (sans rôle, retire la quarantaine)
- `/protect status`

### Groupes de gestion

- `/protect whitelist add|remove|list utilisateur:<user>`
- `/protect staffrole add|remove|list role:<role>`
- `/protect allowlist add|remove|list domaine:<string>`

## 4) Setup recommandé (rapide)

1. Activer les 3 modules :
   - `antibot`, `antiraid`, `antiphishing`.
2. Définir un rôle de quarantaine :
   - `/protect quarantine role:@Quarantine`
3. Régler un seuil de raid réaliste :
   - `/protect raidconfig seuil:10 fenetre:60 duree:300`
4. Ajouter les rôles staff de confiance :
   - `/protect staffrole add role:@Mod`
5. Ajouter les domaines sûrs internes :
   - `/protect allowlist add domaine:ton-domaine.tld`
6. Vérifier la config active :
   - `/protect status`

## 5) Conseils pour bien l’utiliser

- Préfère la **quarantaine** au kick immédiat pour les cas douteux.
- N’ajoute en whitelist que des comptes explicitement fiables.
- Revois régulièrement l’allowlist phishing (évite les wildcards imprécis).
- Surveille les logs modération pour ajuster seuils/fenêtres.

