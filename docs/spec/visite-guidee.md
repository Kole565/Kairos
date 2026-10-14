# Visite guidée enrichie et catalogue des fonctionnalités

_Rôle : faire connaître à l'utilisateur toutes les fonctionnalités de Kairos,
au bon moment, depuis une source unique. Fichiers couverts (à venir) :
`kmp/ui/.../ui/guide/GuideContent.kt`, `GuideDialog.kt`, `HelpDialog.kt`,
`ui/screens/HomeScreen.kt`, `ui/app/Welcome.kt`, ressources `values/` et
`values-fr/`. Tests : `GuideStepsTest`, `M6GuideUiTest`, `StringsParityTest`,
tests nommés dans les critères._

État : **issue #64 (retour du propriétaire)**, **spécifiée le 2026-10-13, non
implémentée**. Livraison visée : 3.4.0. **Rouvre explicitement** les décisions
de `accueil.md` § La visite guidée (deux listes de 6 étapes écrites en dur,
aucune persistance) ; le reste de `accueil.md` est inchangé.

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

`FeatureCard(id, chapter, title, body, target, since)` : un seul catalogue dans
`ui/guide`, avec `since` = version de livraison (`"3.4.0"`). `GuideKind` actuel
(visite initiale / équipe) devient une **sélection** du catalogue
(`GuideSteps.of(kind)`), plus une liste écrite en dur. `GuideTarget` gagne une
clé de section (par exemple `Day.Blocked`) pour atterrir au bon endroit.
`HelpDialog` lit les cartes du chapitre de l'écran courant ;
`HomeScreen` affiche les cartes dont `since` égale la version de
`kairos.versionName`.

### Persistance

Aucune : la section « Nouveau » dépend de la version de l'application, pas d'un
« vu ». Un éventuel `Settings.guide` suivrait le patron « nul par défaut » du
reste de la 3.4 (`planning-personnalise.md`).

### Règle de livraison

Toute fonctionnalité visible par l'utilisateur (donc dans le README) ajoute ou
met à jour une carte dans le même changement ; `FeatureCatalogTest` casse sinon.
À ajouter à la skill `kairos-spec` (§ 4) à l'implémentation.

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

### Impacts sur les specs existantes (à reporter à l'implémentation)

`accueil.md` (visite et aide lisent le catalogue, section « Nouveau »),
`navigation-theme.md` (« À propos et guide »), `reglages.md` (entrée « Découvrir
Kairos »), `accessibilite.md`, `i18n.md`, `.claude/skills/kairos-spec/SKILL.md`
(règle de livraison).
