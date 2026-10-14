# Réglages

_Rôle : tout ce qui se règle dans Kairos, depuis un seul écran, avec une
validation par champ. Fichiers couverts :
`kmp/core/src/commonMain/.../core/settings/SettingsForm.kt` (champs, bornes,
validation, pur), `kmp/core/.../model/Settings.kt` (modèle, décrit par
`modele-donnees.md`), `kmp/ui/.../settings/` (`SettingsScreen.kt`,
`SettingsFormCards.kt`). La carte Données est décrite par `export-import.md`,
la carte Mises à jour par `mises-a-jour.md`, la carte
Apparence par `apparence.md`, la carte Langue par `i18n.md`. Tests :
`core/.../settings/SettingsFormTest.kt`,
`M5ScreensUiTest.anInvalidFieldBlocksSavingThenAValidFormIsSaved`._

État : **jalon M5**. Kairos n'a aucun secret à stocker (pas de trousseau) ni
de réglage de chemin de base, de proxy ou de niveau de log : tout ce qui se
règle l'est depuis ce formulaire.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos démarre sans rien configurer ; tout ce qui se règle se règle depuis
l'application, jamais dans un fichier. Un réglage faux (heure 25, texte dans
un nombre) ne doit jamais être enregistré, ni à moitié.

### Comportement attendu (utilisateur)

- Destination « Réglages », une carte par section, dans cet ordre (3.4, `planning-personnalise.md` lot A : **Langue** et
  **Apparence** viennent **en premier**, puis les cartes ci-dessous ; **Score de priorité** et **Creux de l'après-midi** sont
  regroupés dans un groupe replié « Avancé : comment les tâches sont ordonnées », ouvert d'office quand un de leurs champs est
  en erreur ; une carte « Réglages par défaut » avec **« Restaurer les valeurs par défaut »** précède « Données »)
  :
  **Journée de travail** (durée par défaut d'une tâche, marge après un
  créneau occupé, début et fin de journée), **Score de priorité (WSJF)**
  (base de valeur, horizon d'urgence, poids maximal de l'urgence, points par
  défaut), **Creux de l'après-midi** (activé, début, creux le plus profond,
  fin, force de la pénalité), **Garde-fous** (seuils « traîne » en retard et
  sans date, seuil de surcharge P0), **Types de tâches**, **Statistiques**
  (fenêtre en semaines), **Alertes du chrono** (chrono oublié, pause
  suggérée, son de secours), **Jours fériés** (français, dates
  supplémentaires), **Langue** (Système, Français ou English, avant
  Apparence ; `i18n.md` § Choix de la langue), **Apparence** (couleur du
  thème, `apparence.md`),
  **Équipe** (interrupteur « Gestion d'équipe », puis, mode activé, nom de
  l'équipe et du manager, seuils « sans avancement », « ballottée » et
  limite d'en-cours, horizon, taux de focus, heures par point, seuil
  d'alerte de charge et tolérance d'affinité (`equipe-charge.md`),
  tirages par simulation, historique, échantillon minimum et seuil
  d'échéance en danger (`equipe-simulation.md`), « Supprimer les données d'équipe… » avec sauvegarde
  préalable ; désactivation confirmée, `equipe.md`,
  `equipe-backlog-suivi.md`), puis
  sur le bureau **Mises à jour**, sur une copie portable **Raccourci**
  (`raccourci-portable.md`), puis **Données** et
  « À propos et guide ».
- Chaque champ a un libellé court et, dessous, une phrase qui dit à quoi il
  sert ; un booléen est un interrupteur dont
  toute la ligne se touche.
- Une barre fixée en bas porte « Enregistrer » (actif dès qu'une valeur
  change), « Annuler les modifications » et « Modifications non
  enregistrées ». Un seul « Enregistrer » pour tout le formulaire.
- À l'enregistrement : chaque champ invalide affiche son erreur à la place de
  son aide (« Au plus 23. », « Un nombre entier est attendu. », « Date
  illisible : « 25/12 » (format AAAA-MM-JJ). »…), un message dit
  « Réglages non enregistrés », et **rien** n'est enregistré. Corriger un
  champ efface son erreur. Les règles entre champs (début de journée avant
  la fin ; début ≤ creux le plus profond ≤ fin) s'affichent dans un bandeau
  en tête, une fois chaque champ valide. Un enregistrement refusé **défile**
  jusqu'au premier champ fautif (sinon jusqu'au bandeau) : à 360 dp, le champ
  est trois cartes plus bas et le message « Réglages non enregistrés » ne disait
  pas où regarder (`ErrorReveal` : un `BringIntoViewRequester` par clé, fourni par
  `LocalErrorReveal`).
- Une décimale s'écrit avec une virgule ou un point ; en français, le
  formulaire l'affiche à la virgule (« 0,8 », comme son aide).
- Les réglages valent aussitôt partout (planning, score, alertes, stats).
- « Restaurer les valeurs par défaut » remplit le formulaire des valeurs par défaut (`SettingsForm.restoreDefaults`), **sauf** les
  champs `team.*`, jamais touchés ; rien n'est écrit avant « Enregistrer » et « Annuler les modifications » revient en arrière.
- « WSJF » n'est plus dans le titre de la section du score ni dans l'aide de l'agenda (jargon ; il reste expliqué dans « À propos
  et guide »).

### Critères de succès

- Valeurs par défaut relues puis revalidées à l'identique ; un champ absent
  garde sa valeur (`SettingsFormTest`).
- Hors bornes, vide, non entier, non nombre, date illisible : une erreur par
  champ, aucun enregistrement ; bornes propres de Kairos (figées par `SettingsFormTest`), borne basse exclue pour
  la base de valeur (`SettingsFormTest`).
- Heure 25 refusée, début après la fin refusé, puis 8 h, base 2,5 et creux
  désactivé (dans le groupe « Avancé », ouvert à la main) enregistrés d'un coup (`M5ScreensUiTest`).
- « Restaurer les valeurs par défaut » remplit le formulaire sans rien enregistrer ; « Enregistrer » les pose
  (`M5ScreensUiTest.restoringTheDefaultsFillsTheFormAndStillNeedsASave`) ; les champs `team.*` ne bougent pas
  (`SettingsFormTest.restoring_the_defaults_resets_every_field_but_the_team_ones`).

### Hors périmètre / différé

- Choix de la langue (on suit le système, `i18n.md`), thème sombre
  (`navigation-theme.md`, `apparence.md`).
- Réglage du chemin de la base, d'un proxy ou du niveau de journalisation (voir
  l'en-tête).

## 2. Solution technique

### Champs (`SettingsForm`, `core`)

- `FIELDS` : un `SettingField` par réglage éditable, clé = nom du champ de
  `Settings` (préfixée `team.` pour ceux de `Settings.team` : `team.enabled`,
  `team.name`, `team.managerName` ; tant que `team` est nul, y écrire la
  valeur par défaut le laisse nul, pour qu'enregistrer le formulaire d'un
  solo ne crée jamais d'objet d'équipe), nature (`INT`, `DECIMAL`, `BOOL`, `TEXT`, `DATES`, `COLOR` ; `language` est un
  `TEXT` normalisé par `UiLanguage.normalize`, jamais refusé), bornes
  incluses, borne basse exclue (`minExclusive`), lecture et écriture.
  Bornes de `app/config.py` : durée par défaut ≥ 1 ; marge, horizon, poids
  de l'urgence, seuils « traîne » et de surcharge, pénalité du creux, seuils
  du chrono ≥ 0 ; heures 0-23 ; base de valeur > 0 ; points par défaut ≥ 1 ;
  fenêtre des stats ≥ 1.
- `values(settings)` : texte de chaque champ (« 4 » plutôt que « 4.0 »,
  booléens « true » / « false »).
- `validate(textes, base)` : chaque champ présent est vérifié ; une clé
  absente garde la valeur de `base`. Erreurs (`FieldError`) : `REQUIRED`,
  `NOT_INTEGER`, `NOT_NUMBER`, `TOO_SMALL` / `TOO_LARGE` (avec la borne),
  `INVALID_DATE`, `INVALID_COLOR` (avec le texte fautif). Puis, seulement sans erreur de
  champ, `GeneralError.WORKDAY_ORDER` (début ≥ fin) ou `DIP_ORDER` (creux
  activé seulement : désactivé, ses heures ne servent pas et ne bloquent
  rien). Un entier trop long pour être lu donne `TOO_LARGE` / `TOO_SMALL`
  avec la borne (ou la limite d'un entier), pas `NOT_INTEGER`. Rend les
  réglages complets, ou `null` à la moindre erreur. `ExportCodec.decode`
  s'en sert aussi pour refuser un export aux réglages hors bornes.
- Nettoyage à l'écriture : textes sans espaces de bord ; dates
  supplémentaires et types proposés sans vides **ni doublons** (le premier
  reste), séparés par « , » (`normalizeList`) ; couleur normalisée en
  `#RRGGBB` majuscules (`apparence.md`).

### Écran (`SettingsScreen`, `ui`)

- État : textes du formulaire, erreurs et règle violée, réinitialisés quand
  les réglages enregistrés changent (import d'un export) ; `dirty` = textes ≠
  valeurs enregistrées.
- `SettingsFormCards` : carte « filled » par section (`SettingsCard`, sans contour, `densite.md` ; titre marqué
  `heading`), `SettingInput` : `OutlinedTextField` (largeur bornée à 320 dp pour un nombre, pleine largeur pour un texte), clavier
  numérique ou décimal, `supportingText` = erreur sinon aide ; `SwitchRow` :
  `Switch` dans une ligne `toggleable` (rôle interrupteur, 48 dp).
- Bandeau de règle inter-champs : conteneur d'erreur et icône `Warning`
  (un vrai échec, charte). `SaveBar` : `Surface` `surfaceContainer` sous la
  colonne défilante, `Button` « Enregistrer », `TextButton` « Annuler les
  modifications ».
- Enregistrer : `SettingsForm.validate` puis `updateSettings`, message
  « Réglages enregistrés. » ou « Réglages non enregistrés… ».

### Décisions et pièges tracés

- **Un seul formulaire, un seul « Enregistrer »** : la carte
  du chrono du jalon M3, qui avait son propre bouton, y est fondue
  (`ChronoSettingsCard` retiré).
- **Validation à l'enregistrement, pas à la frappe** : on ne crie pas
  « Obligatoire » pendant qu'on efface pour retaper.
- **Dates supplémentaires validées** : une date illisible est signalée
  plutôt qu'ignorée en silence (même format, rien d'autre ne change).
- **Virgule décimale acceptée** : « 2,5 » est la façon française de
  l'écrire.
- **Plus de carte « En construction »** : toutes les parties des Réglages
  sont portées ; `UnderConstruction`, ses textes et l'icône `Construction`
  sont retirés.
