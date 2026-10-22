# Visite guidée enrichie et catalogue des fonctionnalités

_Rôle : faire connaître à l'utilisateur toutes les fonctionnalités de Kairos,
au bon moment, depuis une source unique. Fichiers couverts :
`kmp/ui/.../ui/guide/FeatureCatalog.kt`, `GuideContent.kt`, `GuideDialog.kt`,
`DiscoverDialog.kt`, `HelpDialog.kt`, `ui/screens/HomeScreen.kt`,
`ui/settings/SettingsScreen.kt`, `ui/navigation/AppShell.kt`, ressources `values/`
et `values-fr/`. Tests : `FeatureCatalogTest`, `GuideStepsTest`, `M6GuideUiTest`,
`StringsParityTest`._

État : **issue #64 (retour du propriétaire)**, spécifiée le 2026-10-13,
**implémentée en 3.4.0** (voir « Écarts avec la spécification initiale »).
**Rouvre explicitement** les décisions de `accueil.md` § La visite guidée (deux
listes de 6 étapes écrites en dur, aucune persistance) ; le reste de `accueil.md`
est inchangé.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La visite actuelle compte deux listes de six étapes fixes. Elle ne parle ni de
l'équipe, ni des sous-tâches, ni de la frise et du temps réel, ni des
statistiques, ni des réglages, et rien ne dit ce qui est nouveau d'une version à
l'autre. Les chantiers de la 3.4 (horaires par jour, durées apprises, déplacer
une tâche…) ajoutent des fonctionnalités qu'il faut faire
découvrir sans que l'utilisateur ait à lire une documentation.

### Comportement attendu (utilisateur)

1. **Une visite initiale courte** (six étapes environ) reste proposée à
   l'accueil du premier lancement.
2. **Un hub « Découvrir Kairos »**, accessible depuis Réglages et l'aide, liste
   des **chapitres** et permet d'en rouvrir un à tout moment : Premiers pas ;
   Planifier (priorité, points, score, horaires par jour, heures préférées) ;
   Suivre (chrono, durées apprises, statistiques) ; Organiser (dépendances,
   sous-tâches, récurrences, déplacer) ; Équipe (si l'espace est activé) ;
   Réglages.
3. **Chaque carte pointe vers l'écran** qu'elle décrit (« Voir l'écran »), vers la
   section précise quand elle existe.
4. **« Nouveau dans cette version »** : sur l'accueil, une section liste les
   cartes ajoutées par la version courante, masquable pour la session. Rien
   n'est mémorisé d'une version à l'autre.
5. **L'aide d'un écran** (bouton « ? ») et la visite lisent les **mêmes
   textes** ; aucun texte n'est écrit à deux endroits.
6. L'accompagnement de la **première tâche** (`premiere-tache.md`) fait partie
   des chapitres et réutilise ces cartes.

### Critères de succès

- Chaque carte du catalogue a ses textes FR et EN et une cible qui existe
  (`FeatureCatalogTest`).
- Chaque fonctionnalité listée dans `README.md` correspond à au moins une carte
  (`FeatureCatalogTest.readmeFeaturesAreCovered`, sur une liste d'identifiants
  maintenue à côté du catalogue).
- La visite initiale, l'aide et la section « Nouveau » lisent le catalogue
  (`GuideStepsTest`, `M6GuideUiTest` mis à jour).
- Les cartes « Équipe » n'apparaissent que si l'espace est activé.
- Textes FR/EN (`StringsParityTest`), 360 dp sans débordement, cibles de 48 dp,
  captures `--self-test` regardées.

### Hors périmètre / différé

- Mémoriser ce que l'utilisateur a « vu » (option ultérieure : `Settings.guide`
  nullable, qui resterait nul tant que rien n'est écrit).
- Visite interactive qui manipule l'écran à la place de l'utilisateur.
- Vidéos ou animations.

## 2. Solution technique

### Catalogue piloté par les données

`FeatureCatalog.cards` (`ui/guide/FeatureCatalog.kt`) : une liste de `FeatureCard(id, chapter, icon, title, body, target,
since, tour)`, **dans l'ordre de la visite**.

- **Chapitres** (`GuideChapter`) : `FIRST_STEPS` (capture, qualification, « faire dans l'ordre », « Découvrir Kairos »,
  première tâche), `PLAN` (priorité, points et estimation ; score et placement ; plages et contextes ; meilleures heures, 3.6 ; horaires par jour), `FOLLOW` (chrono ;
  durées apprises ; semaine ; statistiques), `ORGANIZE` (sous-tâches ; récurrence et déplacer ; bloqueurs ; créneaux et
  deep work), `TEAM` (les six cartes d'équipe), `SETTINGS` (réglages clairs ; données). `FeatureCatalog.chapters(teamEnabled)`
  n'offre `TEAM` que si l'espace Équipe est activé.
- **Visites** : `GuideSteps.of(GuideKind.PERSONAL)` = les cartes `tour = true` (six, comme avant) ;
  `GuideSteps.of(GuideKind.TEAM)` = le chapitre `TEAM` (six). Les textes des douze cartes d'avant gardent leurs
  chaînes `guide_you_*` et `guide_team_*` ; les autres sont `card_<id>_title` / `card_<id>_body`.
- **Cibles** (`GuideTarget`) : `Personal(destination)`, `Team(destination, exchanges)`, `Settings` et `FirstTask`
  (lance l'accompagnement de `premiere-tache.md`, sans changer d'écran ; le bouton devient « Créer ma première tâche »).
- **`since`** : version de livraison ; `FeatureCatalog.newIn(version)` rend les cartes de **mêmes numéros majeur et mineur**
  (`3.4.1` ou `3.4.0-beta.2` gardent la section de la `3.4.0` ; une version illisible ou sans carte rend une liste vide).
- **Lien avec le README** : `FeatureCatalog.README_FEATURES` associe chaque fonctionnalité en gras de la section
  « Features » de `README.md` à une carte ; `FeatureCatalogTest.readmeFeaturesAreCovered` lit le README et compare.
- **`GuideDialog`** accepte n'importe quelle sélection (`GuideDialog(steps, key, …)`, `GuideDialog(kind, …)` en est la
  forme courte) et garde la mesure du plus long titre et du plus long texte.
- **Hub « Découvrir Kairos »** (`DiscoverDialog`) : « Créer ma première tâche » puis un chapitre par ligne avec son nombre
  de cartes ; choisir un chapitre ferme le hub et ouvre ses cartes dans `GuideDialog` ; accessible depuis l'Accueil (bouton),
  l'aide (entrée « Découvrir Kairos ») et les Réglages (carte « Découvrir Kairos » de la catégorie « À propos », `reglages.md`) ; états `discover` et `chapter` de
  `AppShell` (`rememberSaveable`, rien n'est mémorisé au-delà).
- **« Nouveau dans cette version »** : `HomeScreen(version = KairosBuild.VERSION_NAME)` affiche, sous le bloc teinté, les
  cartes de `newIn(version)` (toute la carte ouvre sa cible) avec un bouton « Masquer » valable pour la session
  (`rememberSaveable`).

### Persistance

Aucune : la section « Nouveau » dépend de la version de l'application, pas d'un
« vu ». Un éventuel `Settings.guide` suivrait le patron « nul par défaut » du
reste de la 3.4 (`planning-personnalise.md`).

### Règle de livraison

Toute fonctionnalité visible par l'utilisateur (donc dans le README) ajoute ou
met à jour une carte dans le même changement ; `FeatureCatalogTest` casse sinon (règle consignée dans la skill `kairos-spec`, § 4).

### Décisions et pièges tracés

- **Catalogue unique** (écarté : garder des listes par écran, qui divergent) : la
  dérive entre visite, aide et README est le défaut à éviter.
- **Pas de « vu » persistant** (écarté pour la 3.4) : évite un réglage de plus ;
  la section « Nouveau » est peu intrusive.
- **Visite initiale courte** (écarté : une visite exhaustive d'emblée) : un
  nouveau venu abandonne une visite de vingt étapes ; le hub porte la profondeur.
- **Titres de l'étape** : le dialogue mesure déjà le plus long titre et le plus
  long texte pour que les boutons ne sautent pas ; le catalogue conserve ce
  comportement (`GuideDialog`).

### Écarts avec la spécification initiale (décidés à l'implémentation)

- **Pas de clé de section dans `GuideTarget`** (ex. `Day.Blocked`) : une carte ouvre l'écran, pas un endroit précis de
  l'écran. Différé : il demande un défilement ciblé dans chaque écran, sans équivalent aujourd'hui.
- **L'aide d'un écran garde ses textes `help_*`** (mode d'emploi de l'écran) ; elle renvoie au hub par une entrée. Le catalogue
  décrit les fonctionnalités, pas le mode d'emploi de chaque écran : les deux ne se recouvrent pas, donc rien n'est écrit
  deux fois.
- **Chapitres de six cartes ou moins** : « Premiers pas » en compte cinq (dont « Découvrir Kairos » et la première tâche).
