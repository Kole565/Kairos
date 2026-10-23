# Apparence : couleur et thème sombre de Kairos

_Rôle : laisser chacun choisir la couleur dont tout le thème est dérivé, et un thème clair ou sombre.
Fichiers couverts : `kmp/core/.../model/Settings.kt` (`themeColor`),
`kmp/core/.../settings/SettingsForm.kt` (champ `themeColor`, validation),
`kmp/ui/.../theme/` (`ThemeColors.kt` : graines proposées et schéma dérivé ;
`KairosTheme.kt`), `kmp/ui/.../settings/AppearanceCard.kt`,
`kmp/ui/.../KairosApp.kt` (thème appliqué une fois les réglages lus,
paramètres `systemColorScheme`, `systemDark`, `onDarkTheme`), `kmp/core/.../settings/ThemeMode.kt` (thème résolu,
interrupteur), `kmp/tools/make_theme.py` (schémas clair et sombre), `kmp/androidApp/.../MainActivity.kt`
(couleurs du système sur Android 12 et plus, barres du système), `kmp/androidApp/src/main/res/values-night*/`
et `kmp/webApp/.../index.html` (relais natifs en sombre). Tests :
`core/.../settings/SettingsFormTest.kt`, `ui/.../theme/ThemeColorsTest.kt`,
`desktopApp/.../M5ScreensUiTest.kt`, `core/.../settings/ThemeModeTest.kt`, `desktopApp/.../DarkThemeUiTest.kt`._

État : couleur, issue #45 (après la 3.0.0) ; thème sombre, issue #72 (3.7.0).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Le thème de Kairos est dérivé d'une seule couleur, le miel du logo
(`docs/DESIGN_SYSTEM.md`). Certains préfèrent une autre couleur, ou celle de
leur téléphone. Changer de couleur ne doit pas casser la charte : contrastes,
rôles et règles d'usage de la couleur restent ceux de Material Design 3.

### Comportement attendu (utilisateur)

- Réglages → catégorie « Général » → carte **Apparence**, après « Langue » et avant « Mises à jour » (`reglages.md`) :
  - une rangée de **pastilles** : le miel (par défaut) et sept autres
    couleurs nommées ; la pastille choisie porte une coche ;
  - sur **Android 12 et plus**, une pastille « Couleurs du système » en tête :
    le thème suit alors les couleurs du fond d'écran (Material You) ;
  - un champ **« Couleur personnalisée »** (`#RRGGBB`, par exemple `#2F6FED`) :
    une couleur saisie remplace la pastille choisie ; choisir une pastille
    remplit le champ avec sa couleur.
- Comme tout réglage, le choix ne s'applique qu'à **« Enregistrer »**
  (`reglages.md`) ; tout l'écran change alors de couleur.
- Une couleur illisible est refusée avec son erreur sous le champ
  (« Couleur illisible : « bleu » (format #RRGGBB). »), et rien n'est
  enregistré.
- L'aide de la carte rappelle que tout le thème est dérivé de la couleur
  choisie, et que le rouge reste réservé aux urgences et aux erreurs.
- Le choix voyage avec les autres réglages (export, import). « Couleurs du
  système » importé sur un appareil qui n'en a pas (bureau, web, Android
  avant 12) donne le miel.

### Thème sombre (issue #72, 3.7.0)

- Par défaut, Kairos **suit le système** : sombre si le téléphone, l'ordinateur ou le navigateur est en sombre, clair
  sinon (une installation existante passe donc en sombre à la mise à jour si son système l'est).
- Carte **Apparence**, en tête : un interrupteur à **deux états** « Thème sombre », qui montre le thème réellement
  affiché. Le basculer vers l'inverse de ce que fait le système **choisit** ce thème ; le rebasculer vers ce que fait le
  système revient à **suivre le système** (logique de l'issue, d'après Lea Verou : pas de troisième état « Système » à
  l'écran). Une ligne dessous le dit : « Suit le système. » ou « Choisi ici : rebascule-le pour suivre à nouveau le
  système. ». Comme tout réglage, il ne s'applique qu'à « Enregistrer ».
- Un thème choisi **reste** choisi quand le système change ensuite ; seul l'interrupteur le rend au système.
- En sombre, **tout le schéma** est celui de la même graine (miel ou autre, couleurs du système sur Android 12 et plus),
  calculé par le même algorithme : les règles d'usage de la couleur ne changent pas (un seul bloc teinté, rouge réservé,
  tertiaire pour le deep work, vert « ok » pour le fait). La surface inverse (carte « En ce moment », snackbar d'alerte)
  devient claire sur fond sombre, comme le veut Material 3.
- Le logo garde ses couleurs (cadran crème) ; les pastilles de la carte Apparence montrent toujours la graine.
- Avant que les réglages soient lus (écran de démarrage, chargement), et dans les relais natifs (fenêtre de démarrage
  Android, page de chargement web), le thème est celui du système.
- Sur Android, les icônes des barres du système (heure, batterie, navigation) suivent le thème affiché.
- Les **images exportées** des Stats (`statistiques.md` § Export) restent **claires**, sur la même graine : une image se
  colle dans un rapport ou s'imprime.
- Les fiches des magasins et la page gardent leurs captures claires et en ajoutent une **en sombre** (la vue Jour,
  capture 9, `publication.md`).

### Critères de succès

- Pour la graine miel, le schéma calculé est **exactement** celui de la
  charte (les 36 rôles de `KairosColors.kt`, générés par
  `kmp/tools/make_theme.py`).
- Pour toute graine, les rôles viennent du même algorithme (Tonal spot,
  spec 2021) : les contrastes texte/fond restent ceux de MD3.
- Choisir une pastille puis « Enregistrer » change la couleur primaire de
  l'interface ; une couleur illisible bloque l'enregistrement.
- Pour la graine miel, le schéma sombre calculé est exactement celui généré par `make_theme.py`
  (`KairosDarkColors`) ; la couleur « ok » sombre a les tons de l'erreur sombre (`ThemeColorsTest`).
- Le thème résolu : « suivre le système » donne le thème du système ; « sombre » ou « clair » l'emportent sur lui ;
  basculer l'interrupteur vers le thème du système rend « suivre le système », jamais une valeur égale au système ; une
  valeur inconnue (import) revient à « suivre le système » (`ThemeModeTest`).
- Système clair, basculer « Thème sombre » puis « Enregistrer » assombrit l'interface (fond `surface` sombre) ; le
  rebasculer rend « suivre le système » ; système sombre, Kairos est sombre sans rien régler (`DarkThemeUiTest`).
- En sombre, l'image exportée d'une carte des Stats reste claire (`StatsExportUiTest`).

### Hors périmètre

- Un contraste élevé, un thème « noir pur » (OLED), un réglage du sombre par écran ou selon l'heure.
- Un aperçu en direct avant « Enregistrer » (règle de tous les réglages).
- Logo, icônes d'application, page de téléchargement et fiches des magasins :
  ils gardent le miel, couleur de l'identité de Kairos.
- Couleur « fait » (vert « ok ») et rouge d'erreur : fixes, quelle que soit la
  graine.

## 2. Solution technique

### Réglage (`core`)

- `Settings.themeColor: String`, par défaut `"#C28417"`
  (`Settings.DEFAULT_THEME_COLOR`) ; la valeur `"system"`
  (`Settings.SYSTEM_THEME`) demande les couleurs du système.
- `Settings.themeMode: String`, par défaut `"system"` (`Settings.THEME_MODE_SYSTEM`), ou `"light"` / `"dark"`
  (`THEME_MODE_LIGHT`, `THEME_MODE_DARK`). `ThemeMode` (`core/settings`, pur) : `normalize(valeur)` (inconnu ou vide →
  `system`), `isDark(mode, systèmeSombre)` (le thème résolu), `toggled(voulu, systèmeSombre)` (la valeur que pose
  l'interrupteur : `system` si `voulu` égale le système, sinon `dark` ou `light`). Le réglage entre dans l'export
  (le fichier d'or `solo-export-v1.json` le montre) ; une version plus ancienne l'ignore à l'import
  (`ignoreUnknownKeys`).
- `SettingsForm` : champ `themeMode` (texte normalisé par `ThemeMode.normalize`, jamais refusé, comme `language`) ;
  champ `themeColor`, nature `FieldKind.COLOR`. Accepte
  `system` ou six chiffres hexadécimaux, avec ou sans `#`, en majuscules ou
  minuscules ; enregistre la forme normalisée `#RRGGBB` (majuscules) ou
  `system`. Sinon erreur `FieldErrorKind.INVALID_COLOR` (le texte saisi en
  borne). `SettingsForm.parseColor` rend la couleur `0xRRGGBB` ou `null`.

### Schéma (`ui/theme`)

- Algorithme : `com.materialkolor:material-color-utilities` 5.0.1 (MIT,
  portage Kotlin multiplateforme de material-color-utilities de Google, sans
  dépendance Compose) ; `SchemeTonalSpot(Hct.fromInt(argb), isDark = false,
  contrastLevel = 0.0, SpecVersion.SPEC_2021)`, dont les 36 rôles remplissent
  un `lightColorScheme`. C'est le calcul que le script `make_theme.py`
  (bibliothèque `materialyoucolor`) fait pour produire `KairosColors.kt`.
- `ThemeColors` : les graines proposées (`PRESETS`, nom localisé + couleur) :
  Miel `#C28417`, Océan `#2F6FED`, Sarcelle `#1B8A8A`, Indigo `#5B5FC7`,
  Lavande `#8A5CC2`, Framboise `#B83E7A`, Ardoise `#56687A`, Terre
  `#9A5B3F` ; `schemeFor(themeColor, system)` : les couleurs du système si
  `system` et que la plateforme en fournit, `KairosLightColors` pour le miel
  (valeur exacte générée), le schéma calculé sinon.
- Sombre : `make_theme.py` génère aussi `KairosDarkColors` (`SchemeTonalSpot(…, isDark = True, …)`) ;
  `schemeFor(themeColor, system, dark)` et `seedScheme(rgb, dark)` rendent un `darkColorScheme` aux mêmes 36 rôles.
- `KairosTheme(scheme, dark)` : applique le schéma ; typographie et formes inchangées ; couleur « ok » claire
  (`#36693D` / `#B7F1B8` / `#1D5127`, charte) ou sombre (`KairosExtraColors.dark()` : `#9CD49F`, ton 80 de la
  palette teinte 150°, chroma 36 ; `#1D5127` / `#B7F1B8`, conteneur et texte échangés, comme l'erreur sombre).
  `KairosDefaultTheme(sombre)` : le miel clair ou sombre, avant la lecture des réglages. `LocalLightColorScheme` : le schéma **clair** de la graine
  courante, pour ce qui doit rester clair (images exportées des Stats).
- `KairosApp(platform, systemColorScheme = null, systemDark = null, onDarkTheme = {}, …)` : `systemColorScheme` devient
  une fonction `(sombre) -> ColorScheme` (Android : `dynamicDarkColorScheme` ou `dynamicLightColorScheme`) ;
  `systemDark` force le thème du système (tests, captures), sinon `isSystemInDarkTheme()` ; le thème résolu est
  `ThemeMode.isDark(settings.themeMode, systemDark)` dès que les réglages sont lus, celui du système avant ;
  `onDarkTheme(sombre)` est rappelé à chaque changement du thème résolu.
- `KairosApp(platform, systemColorScheme = null, …)` : thème par défaut pour
  l'écran de chargement, puis le thème des réglages dès qu'ils sont lus
  (recalculé quand `themeColor` change). `LocalSystemColorScheme` indique à
  la carte Apparence si « Couleurs du système » est disponible.
- Android : `MainActivity` passe `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` sur
  Android 12 et plus (API 31), `null` avant ; recalculé par Compose quand le
  fond d'écran change. `onDarkTheme` rappelle `enableEdgeToEdge(SystemBarStyle.auto(…) { sombre })` : icônes des
  barres claires sur fond sombre. Fenêtre de démarrage : `values-night/` (fond `surface` sombre, barres sombres,
  parent `Theme.Material.NoActionBar`), le système la choisit d'après son propre mode.
- Web : la page de chargement (`index.html`) prend les couleurs sombres sous `prefers-color-scheme: dark`, et
  `theme-color` a sa variante sombre. Bureau : rien (décorations de fenêtre du système).

### Carte Apparence (`ui/settings/AppearanceCard.kt`)

- `OutlinedCard` comme les autres sections : titre, aide, interrupteur « Thème sombre » (`SwitchRow`, ligne
  `toggleable` entière, état coché = `ThemeMode.isDark(valeur du formulaire, isSystemInDarkTheme())`, ligne d'aide
  selon que la valeur est `system` ou non), pastilles (`FlowRow`), champ `OutlinedTextField` du réglage `themeColor`.
- Pastille : disque de 36 dp de la couleur de la graine dans une cible de
  48 dp ; la choisie porte un contour `onSurface` et une coche `Check`
  blanche ou noire selon la luminance de la pastille ; `Role.RadioButton`,
  état « sélectionné » et nom de la couleur pour les lecteurs d'écran.
  « Couleurs du système » : pastille dessinée avec le `primary` du système.
  Une couleur libre valide, hors graines proposées, a sa propre pastille
  (cochée, nommée par sa valeur `#RRGGBB`).
- Le champ affiche `#RRGGBB` (vide pour « Couleurs du système ») ; l'erreur
  du champ remplace son aide.

### Décisions et pièges tracés

- **Une graine, jamais un rôle isolé** (règle de la charte) : l'utilisateur
  choisit une graine, tout le schéma en est recalculé par l'algorithme MD3 ;
  aucun rôle n'est réglable seul.
- **Miel servi depuis `KairosColors.kt`** plutôt que recalculé : la charte
  reste la source des valeurs par défaut ; `ThemeColorsTest` vérifie que
  l'algorithme embarqué redonne exactement ces 36 valeurs, garantie que toute
  autre graine suit le même calcul.
- **Pastilles de couleur en `Color(argb)`** : la seule couleur de composant
  hors schéma, parce que la pastille montre la donnée elle-même (la graine),
  pas un rôle ; sa coche est blanche ou noire pour rester lisible sur
  n'importe quelle graine.
- **Pas de rouge ni de vert parmi les graines proposées** : le rouge est
  réservé aux urgences et aux erreurs, le vert « ok » au fait. Une couleur
  libre reste permise ; l'aide le rappelle.
- **Deux états à l'écran, trois en base** (choix du propriétaire, issue #72) : l'interrupteur montre ce qu'on voit ; un
  troisième état « Système » n'aide qu'au moment où l'on change, et ce moment est justement celui où le thème déplaît.
  `toggled` ne stocke jamais une valeur égale au système (elle figerait le thème en silence), et un changement du
  système ne retire pas un choix fait (les deux erreurs relevées par l'issue).
- **Suivre le système par défaut** (choix du propriétaire) : un écran sombre la nuit ne s'éclaire plus d'un coup ;
  écarté : clair par défaut, qui ne changeait rien aux installations existantes.
- **Interrupteur dans la carte Apparence, à « Enregistrer »** (choix du propriétaire) : la règle de tous les réglages ;
  écarté : un bouton dans la barre du haut, appliqué tout de suite.
- **Images exportées toujours claires** (choix du propriétaire) : elles vont dans des rapports et à l'impression.
- **Schéma sombre généré, jamais retouché** : même règle que le clair ; `KairosDarkColors` sert le miel comme
  `KairosLightColors`, et `ThemeColorsTest` vérifie que l'algorithme embarqué redonne ses 36 valeurs.
- **« Ok » sombre selon la règle de l'erreur** : `ok` au ton 80 de sa palette (`#9CD49F`, vérifié contre la palette
  par `ThemeColorsTest`), conteneur et texte du conteneur échangés avec ceux du clair, exactement comme
  `errorContainer` sombre égale `onErrorContainer` clair. Les valeurs claires de la charte diffèrent d'un point de la
  palette recalculée (`#36693D` contre `#36693E`) : on les garde telles quelles plutôt que de retoucher la charte.
- **Relais natifs selon le système, pas selon le réglage** : la fenêtre de démarrage Android et la page de chargement
  web s'affichent avant que la base soit lue ; un thème choisi contre le système s'applique donc une fraction de seconde
  après le démarrage, accepté.
- **Couleurs du système en option, pas par défaut** : le miel reste
  l'identité de Kairos sur toutes les plateformes ; Material You n'existe
  que sur Android 12 et plus.
