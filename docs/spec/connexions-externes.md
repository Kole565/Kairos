# Connexions externes : calendriers, sans réseau ni compte

_Rôle : faire entrer dans Kairos les événements des calendriers de
l'utilisateur (Outlook, Google, Thunderbird, Nextcloud…) sous forme de
créneaux occupés, sans compte, sans permission réseau, et garder le cadre
des connexions externes (ce que F-Droid permet, ce que Kairos s'interdit).
Fichiers prévus : `kmp/core/.../core/ics/` (`IcsParser.kt`, `IcsZones.kt`,
`IcsExpander.kt`, `CalendarImport.kt`), `kmp/core/.../core/model/TimeBlock.kt`
(`BlockSource`) et `CalendarSource.kt`, `kmp/data/` (`8.sqm`, table
`calendar_source`, colonnes `time_block.source_id` et `source_uid`,
`ExportCodec`, `KairosRepository.importCalendar` / `removeCalendar`),
`kmp/ui/.../ui/app/CalendarService.kt` et `ExternalBlocks.kt`,
`kmp/ui/.../ui/settings/CalendarsCard.kt`, `kmp/ui/.../ui/settings/CalendarImportDialog.kt`,
`kmp/androidApp/.../AndroidCalendarService.kt`. Tests prévus :
`IcsParserTest`, `IcsZonesTest`, `IcsExpanderTest`, `CalendarImportTest`
(fichiers d'or `core/src/commonTest/resources/ics/`),
`KairosRepositoryTest` (import, réimport, retrait), `CalendarMigrationTest`,
`ExternalBlocksTest`, `CalendarsCardUiTest`._

État : spécifiée le 2026-10-24, non implémentée. Étape 1 : import d'un
fichier `.ics` (issue #136). Étape 2 : calendriers du téléphone sur Android
(issue #138). Suivi : #140. Choix produit validés par le propriétaire le
2026-10-24.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Les réunions vivent dans le calendrier de l'utilisateur, souvent Outlook au
travail. Aujourd'hui il les recopie à la main en créneaux occupés, sinon le
planning pose du travail ou du deep work par-dessus une réunion. Il faut
que Kairos connaisse ces événements **sans** qu'on lui confie un compte et
**sans** que l'application Android demande l'accès au réseau.

Kairos 2 avait un connecteur TimeTree (réseau). Il a été retiré le
2026-09-28 (`docs/plan-v3-kotlin.md` § 2.2), et `ordonnancement.md` classait
depuis les « événements de calendrier externes » hors périmètre. Cette spec
**rouvre cette ligne**, pour des événements qui arrivent par un fichier ou
par le calendrier du téléphone, jamais par le réseau.

### Paliers

| Palier | Ce que l'utilisateur obtient | Permission Android | Où |
|---|---|---|---|
| Fichier | Importer un fichier `.ics` exporté de son calendrier, sur bureau, Android et web | aucune | ici, étape 1 (#136) |
| Calendriers du téléphone | Les événements des calendriers du téléphone, lus en direct (Outlook, Google, DAVx⁵, ICSx⁵…) | lecture du calendrier, demandée à l'activation | ici, étape 2 (#138) |
| Fichier lié | Un fichier Kairos tenu à jour dans un dossier synchronisé | aucune | `export-import.md` (#137) |
| Réseau | Connexion en ligne à un compte ou à un lien de calendrier | réseau | écarté pour l'instant (§ Hors périmètre, #139) |

### Comportement attendu (utilisateur)

#### Importer un fichier `.ics` (étape 1)

- **Obtenir le fichier**, rappelé par l'aide de la carte « Calendriers » :
  Outlook classique « Fichier → Enregistrer le calendrier » ; Outlook sur le
  web « Publier un calendrier » puis ouvrir le lien ICS ; Google Agenda
  « Importer et exporter → Exporter » ; Thunderbird, Nextcloud, Proton,
  Apple : « Exporter ».
- **Deux entrées** : le bouton « Importer » de la carte Données reconnaît
  un fichier iCalendar par son contenu (pas par son extension), et la carte
  « Calendriers » (Réglages → Données) propose « Importer un calendrier… ».
- **Aperçu avant toute écriture** :
  - nom du calendrier, modifiable, prérempli avec le nom inscrit dans le
    fichier, sinon « Calendrier importé » ;
  - case « Masquer les titres » (les créneaux s'appellent alors
    « Occupé »), cochée comme lors du dernier import de ce calendrier ;
  - « N créneaux du JJ/MM au JJ/MM » ;
  - les événements écartés, par raison : libres, annulés, passés, au-delà de
    6 mois, répétition non prise en charge, fuseau horaire inconnu ;
  - si un calendrier de ce nom existe déjà : « Remplace les M créneaux à
    venir de « Nom » » ;
  - « Importer » et « Annuler ». Un fichier sans aucun événement retenu n'a
    que « Fermer ».
- **Ce que devient un événement** : un ou plusieurs créneaux **occupés**,
  à l'heure locale de l'appareil :
  - un événement répété donne un créneau par occurrence, d'aujourd'hui à
    **6 mois** au plus (exceptions et occurrences déplacées respectées) ;
  - un événement qui traverse minuit est découpé par jour ;
  - un événement « journée entière » marqué occupé ou absent occupe la
    journée entière (le planning n'y place rien, la charge de « moi »
    compte la journée comme prise) ; marqué libre (anniversaires,
    rappels), il est écarté ;
  - un événement provisoire compte comme occupé ;
  - un événement libre, annulé ou entièrement passé est écarté.
- **Ce qui est gardé** : le titre (ou « Occupé »), le début et la fin. La
  description, le lieu et les participants ne sont **jamais** stockés.
- **Réimporter** le même calendrier (même nom) remplace ses créneaux **à
  partir d'aujourd'hui** ; ceux du passé restent (trace des journées).
  Les créneaux saisis à la main ne sont jamais touchés.
- **Une sauvegarde automatique** précède l'import, comme pour tout import :
  sans elle, rien n'est écrit.
- **Créneaux importés en lecture seule** : dans la vue Jour et la vue
  Semaine, ils portent une icône de calendrier et « Importé de « Nom » »,
  sans bouton « Modifier ». Pour les corriger, on modifie le calendrier
  d'origine puis on réimporte.
- **Carte « Calendriers »** : chaque calendrier importé avec « N créneaux à
  venir · importé le JJ/MM », « Réimporter » (choisir un fichier, puis
  l'aperçu qui vise ce calendrier) et « Retirer » (confirmation « Retirer
  « Nom » et ses N créneaux ? », passés compris).
- **Fuseau horaire** : les heures sont converties dans le fuseau de
  l'appareil au moment de l'import, puis figées. Après un changement de
  fuseau, on réimporte.
- Les créneaux importés comptent partout comme des créneaux occupés : le
  planning du jour, la vue Semaine, la charge de « moi » dans l'espace
  Équipe. Ils figurent dans l'export de données et en reviennent à
  l'identique. Ils ne partent jamais dans un paquet ou un rapport d'équipe.

#### Calendriers du téléphone (étape 2, Android)

- Carte « Calendriers », section « Calendriers du téléphone » : interrupteur
  « Utiliser les calendriers du téléphone ». La demande d'autorisation du
  système n'apparaît **qu'à ce geste** ; refuser laisse Kairos tel quel.
- Une fois autorisé : la liste des calendriers du téléphone (nom, compte),
  **aucun coché par défaut**.
- Les événements des calendriers cochés apparaissent comme des créneaux
  occupés en **lecture seule**, avec « Calendrier du téléphone « Nom » » :
  mêmes règles qu'à l'import (libres, annulés, refusés écartés ; journée
  entière occupée ; provisoire occupé ; d'il y a un mois à dans 6 mois).
- Ils sont **lus en direct**, jamais stockés, jamais exportés : un
  changement fait dans Outlook ou Google apparaît au retour dans Kairos.
- Un même événement reçu à la fois par un fichier importé et par le
  téléphone n'apparaît qu'une fois (celui du téléphone, plus récent).
- Autorisation retirée dans le système : « Kairos n'a plus accès aux
  calendriers du téléphone. » avec « Ouvrir les réglages du système » ; plus
  aucun créneau du téléphone.
- Outlook pour Android ne copie ses calendriers dans le téléphone que si
  son réglage « Synchroniser les calendriers » est activé (désactivé par
  défaut), et une organisation peut l'interdire. L'aide de la carte le dit
  et renvoie vers l'import de fichier.
- Bureau et web : la section n'existe pas.

### Contraintes

- **Aucune permission réseau sur Android, aucun compte, aucun secret
  stocké.** C'est un choix de Kairos, pas une règle de F-Droid (§ 2,
  Décisions) : il garde l'application sans anti-fonctionnalité F-Droid.
- `core` reste pur : le fuseau de l'appareil et la date du jour sont des
  paramètres.
- Dépendances libres seulement ; ici, aucune nouvelle dépendance.
- Mode solo intact : un utilisateur qui n'importe rien a un export
  identique à l'octet près.

### Critères de succès

- Un fichier Outlook classique (fuseau Windows décrit dans le fichier,
  réunion hebdomadaire avec une occurrence déplacée et une supprimée), un
  fichier Google et un fichier Thunderbird/Nextcloud donnent les créneaux
  attendus, heure par heure, y compris la semaine du changement d'heure
  (`CalendarImportTest`, fichiers d'or).
- Répétitions : quotidienne, hebdomadaire multi-jours, « 2e mardi »,
  « dernier vendredi », « dernier jour ouvré du mois » (`BYSETPOS`),
  intervalle, `COUNT`, `UNTIL`, exceptions, occurrences déplacées et
  annulées (`IcsExpanderTest`).
- Libres, annulés, passés, au-delà de 6 mois, journée entière libre :
  écartés et comptés par raison ; journée entière occupée : un créneau par
  jour (`CalendarImportTest`).
- Réimporter le même fichier ne change rien ; réimporter remplace les
  créneaux à venir du calendrier, garde son passé et ne touche ni les
  créneaux manuels ni ceux d'un autre calendrier ; « Retirer » supprime
  tout le calendrier (`KairosRepositoryTest`).
- Aucun import sans sauvegarde réussie (`KairosRepositoryTest`, comme
  l'import complet).
- Export d'une base sans calendrier importé identique à l'octet près
  (`TeamIsolationTest.soloExportIsByteIdentical`) ; une base avec
  calendriers importés s'exporte et se réimporte à l'identique
  (`KairosRepositoryTest.exportThenImportGivesBackTheSameDatabase`).
- Migration 8 → 9 sur une base existante : aucun créneau modifié
  (`CalendarMigrationTest`).
- Créneaux importés et du téléphone : pas de bouton « Modifier », placement
  du planning et charge de « moi » identiques à des créneaux manuels de mêmes
  heures (`ExternalBlocksTest`, `CalendarsCardUiTest`).
- Calendriers du téléphone : rien n'est écrit en base ni exporté ;
  dédoublonnage par identifiant d'événement et début (`ExternalBlocksTest`) ;
  vérification à la main sur un appareil avec DAVx⁵ ou ICSx⁵ et avec
  Outlook « Synchroniser les calendriers ».
- Un fichier de 5 000 événements est analysé sans blocage de l'interface
  (analyse hors du fil principal) et en moins de 2 s sur le bureau
  (`CalendarImportTest`).
- Textes FR/EN (`StringsParityTest`) ; 360 dp sans débordement ; cibles de
  48 dp.

### Hors périmètre / différé

- **Connexion réseau** (abonnement à un lien ICS, CalDAV, Microsoft 365) :
  pas de permission réseau sur Android, aucune connexion à un compte.
  L'abonnement à un lien ICS **sur le bureau seulement** est une piste
  différée, à rouvrir sur demande (#139). Sur Android, ICSx⁵ s'abonne à ce
  lien et l'étape 2 en lit le résultat.
- Écrire dans un calendrier (créer une réunion, poser un créneau de deep
  work dans Outlook).
- Convertir un créneau importé en créneau manuel (piste notée, non retenue
  pour l'instant).
- Choisir quels types d'événements deviennent du deep work (catégories
  Outlook « Focus ») : tout événement retenu est un créneau occupé.
- Écarter les réunions refusées d'un fichier `.ics` : un export ne dit pas
  quel participant est l'utilisateur. Les exports Outlook ne contiennent
  d'ailleurs pas les réunions refusées.
- Calendriers du système sur le bureau (macOS, Windows).
- Synchronisation entre appareils (#88) et fichier lié (#137,
  `export-import.md`).

## 2. Solution technique

### Modèle (`core/model`)

- `TimeBlock.source: BlockSource?` (nul par défaut : créneau manuel, comme
  aujourd'hui).
- `sealed interface BlockSource { val uid: String }` :
  - `Imported(calendarId: Long, uid: String)` : créneau d'un calendrier
    importé, stocké ;
  - `Device(calendarName: String, uid: String)` : créneau d'un calendrier
    du téléphone, jamais stocké, `id` négatif (synthétique, unique dans la
    liste fournie).
  - `uid` : propriété `UID` de l'événement iCalendar (même valeur pour
    toutes les occurrences d'une série) ; avec `start`, il identifie une
    occurrence pour le dédoublonnage.
- `CalendarSource(id, name, hideTitles: Boolean, importedAt: Instant,
  createdAt: Instant)` ; `KairosSnapshot.calendarSources` (liste vide par
  défaut).
- Un créneau avec `source` non nul a toujours `kind = BUSY` et
  `recurrence = NONE`.

### Lecture iCalendar (`core/ics`, pur, sans dépendance)

- `IcsParser.parse(text): IcsCalendar` : retire un BOM, déplie les lignes
  (RFC 5545 § 3.1), lit nom, paramètres et valeur, déséchappe le texte
  (`\n`, `\,`, `\;`, `\\`). Composants lus : `VCALENDAR` (`X-WR-CALNAME`),
  `VEVENT`, `VTIMEZONE` (`STANDARD`, `DAYLIGHT`). Propriétés de `VEVENT`
  lues : `UID`, `SUMMARY`, `DTSTART`, `DTEND`, `DURATION`, `RRULE`, `RDATE`,
  `EXDATE`, `RECURRENCE-ID`, `STATUS`, `TRANSP`,
  `X-MICROSOFT-CDO-BUSYSTATUS`. Tout le reste est ignoré sans le garder
  (description, lieu, participants, alarmes). Un `VEVENT` sans `UID` reçoit
  un identifiant dérivé de son début et de son titre.
- Détection par le bouton « Importer » : `IcsParser.looksLikeIcs(text)` =
  première ligne non vide `BEGIN:VCALENDAR` (casse ignorée), testée
  **avant** l'aiguillage JSON (`TeamExchangeCodec.detect`).
- `IcsZones` : convertit une heure du fichier en instant.
  1. `…Z` : UTC.
  2. `TZID=…` avec un `VTIMEZONE` de même identifiant dans le fichier :
     décalage calculé depuis ses règles `STANDARD` / `DAYLIGHT`
     (`TZOFFSETFROM`, `TZOFFSETTO`, `DTSTART`, `RRULE` annuelle ou `RDATE`),
     évaluées par `IcsExpander`. Couvre les noms Windows d'Outlook
     (« Romance Standard Time ») sans table de correspondance.
  3. `TZID=…` sans `VTIMEZONE` : `TimeZone.of(tzid)` (nom IANA) ; échec →
     événement écarté, raison « fuseau horaire inconnu ».
  4. Heure flottante (ni `Z` ni `TZID`) et date seule : heure locale de
     l'appareil, telle quelle.
  L'instant est ensuite converti dans le fuseau de l'appareil
  (`deviceZone`, paramètre).
- `IcsExpander.occurrences(event, from, until)` : sous-ensemble de RRULE
  `FREQ` (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`), `INTERVAL`, `COUNT`,
  `UNTIL`, `BYDAY` (avec rang : `2TU`, `-1FR`), `BYMONTHDAY` (négatif
  compris), `BYMONTH`, `BYSETPOS`, `WKST` ; plus `RDATE` et `EXDATE`.
  Autre partie de règle (`BYWEEKNO`, `BYYEARDAY`, `BYHOUR`…, `FREQ`
  infra-journalière) → événement écarté, raison « répétition non prise en
  charge ». Les `VEVENT` de même `UID` avec `RECURRENCE-ID` remplacent
  l'occurrence visée (supprimée si `STATUS:CANCELLED`). L'expansion
  s'arrête à `until` même pour une série sans fin.
- `CalendarImport.plan(calendar, today, deviceZone, hideTitles):
  ImportPlan` : fenêtre `[today 00:00, today + 6 mois]`.
  - Écartés et comptés (`ImportPlan.skipped: Map<SkipReason, Int>`) :
    `FREE` (`TRANSP:TRANSPARENT`, `X-MICROSOFT-CDO-BUSYSTATUS` `FREE` ou
    `WORKINGELSEWHERE`), `CANCELLED`, `PAST` (fin ≤ début de la fenêtre),
    `BEYOND_HORIZON`, `UNSUPPORTED_RULE`, `UNKNOWN_ZONE`.
  - Gardés : `TENTATIVE` et `OOF` comme occupés.
  - Journée entière (`DTSTART;VALUE=DATE`) : un créneau par jour, de
    00:00 au lendemain 00:00.
  - Découpage à minuit de tout événement à cheval sur deux jours.
  - Résultat : `blocks` (créneaux sans `id`), `range` (premier et dernier
    jour), `suggestedName`.
- `MAX_HORIZON_MONTHS = 6` et `DEVICE_LOOKBACK_DAYS = 31` : constantes de
  `CalendarImport`.

### Stockage (`data`)

- Schéma 9 (`8.sqm`, additif) :
  - table `calendar_source(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT
    NOT NULL, hide_titles INTEGER NOT NULL DEFAULT 0, imported_at TEXT NOT
    NULL, created_at TEXT NOT NULL)` ;
  - `time_block.source_id INTEGER` et `time_block.source_uid TEXT`, nuls,
    en fin de table (ordre de `insertTimeBlockWithId`, positionnel) ;
  - index `time_block_source` sur `source_id`.
  Les bases existantes : tous les créneaux restent manuels.
- `KairosRepository.importCalendar(name, hideTitles, plan, today)` : une
  transaction. Elle trouve ou crée le `calendar_source` du nom, supprime ses
  créneaux de début ≥ `today 00:00`, insère ceux du plan, met à jour
  `hide_titles` et `imported_at`. `removeCalendar(id)` supprime la source et
  tous ses créneaux. `updateBlock` et `deleteBlock` refusent un créneau
  importé (garde du dépôt, en plus de l'interface).
- Sauvegarde préalable : `saveBackup(services, "avant-import-calendrier",
  …)`, même mécanisme que les paquets d'équipe ; sans sauvegarde, rien
  n'est écrit.
- `ExportCodec` : `calendarSources` (liste nullable, absente si vide) et,
  par créneau, `sourceId` et `sourceUid` (absents si nuls). Avec
  `explicitNulls = false`, un export sans calendrier ne change pas d'un
  octet. `formatVersion` reste 2 : un lecteur plus ancien ignore ces champs
  (`ignoreUnknownKeys`) et lit les créneaux importés comme des créneaux
  manuels. Validation : un `sourceId` qui ne désigne aucune source est
  refusé (`CORRUPTED`).

### Créneaux externes dans l'interface (`ui/app`)

- `CalendarService` (interface, `AppServices.calendars: CalendarService?`,
  nul sur bureau, web et dans les tests) : `state:
  StateFlow<DeviceCalendarState>` (`Off`, `NeedsPermission`, `Denied`,
  `On(calendars, selected)`), `blocks: StateFlow<List<TimeBlock>>`,
  `enable()`, `disable()`, `select(ids)`.
- `ExternalBlocks.merge(snapshot, deviceBlocks): KairosSnapshot` :
  `snapshot.copy(timeBlocks = …)` avec les créneaux du téléphone ajoutés et,
  pour chaque créneau du téléphone, le créneau importé de même `uid` et de
  même `start` retiré. Seuls les écrans qui **calculent** un planning
  appellent `merge` : `DayScreen`, `WeekScreen`, `LoadCompute`,
  `ForecastState`. L'export, l'import, les échanges d'équipe et l'édition
  lisent toujours `repository.snapshot`. Les créneaux du téléphone ne
  peuvent donc pas être écrits ni exportés.
- `DayBlockList` et la vue Semaine : un créneau à `source` non nul n'a pas
  de bouton « Modifier » ; il porte l'icône `KairosIcons.CalendarMonth`
  (ajoutée à `make_icons.py`) et le libellé de sa source. Couleur : celle
  d'un créneau occupé, aucune couleur de calendrier.

### Android (`androidApp`, étape 2)

- `AndroidCalendarService` : `READ_CALENDAR` ajouté au manifeste, demandé
  par `ActivityResultContracts.RequestPermission` seulement depuis
  l'interrupteur.
- Lecture de `CalendarContract.Calendars` (nom, compte, visibles) et de
  `CalendarContract.Instances` sur `[aujourd'hui − 31 jours, aujourd'hui +
  6 mois]`, hors du fil principal. `Instances` déploie déjà les répétitions,
  les exceptions et les fuseaux : aucun code de répétition ici.
- Colonnes : `TITLE`, `BEGIN`, `END`, `ALL_DAY`, `AVAILABILITY`
  (`AVAILABILITY_FREE` écarté), `STATUS` (`STATUS_CANCELED` écarté),
  `SELF_ATTENDEE_STATUS` (`ATTENDEE_STATUS_DECLINED` écarté),
  `UID_2445`. Mêmes découpages que `CalendarImport` (journée entière,
  minuit), fonction commune de `core`.
- Sélection (identifiants de calendrier, propres à l'appareil) et
  interrupteur gardés dans les préférences de l'application, **jamais**
  dans `Settings` ni dans l'export.
- Rafraîchissement : à chaque reprise de l'activité et par un
  `ContentObserver` sur `CalendarContract.Instances.CONTENT_URI`, regroupé
  (1 s). Autorisation retirée constatée à la reprise → `Denied`, liste vide.

### Interface (`ui/settings`)

- `CalendarsCard`, catégorie Données, entre la carte Données et
  « Réglages par défaut » : texte « Les événements importés deviennent des
  créneaux occupés. Kairos ne se connecte à aucun compte. », bouton
  « Importer un calendrier… », liste des calendriers importés
  (« Réimporter », « Retirer »), puis la section Android.
- `CalendarImportDialog` : l'aperçu décrit en partie 1. Le plan est
  recalculé quand on coche « Masquer les titres » ou qu'on change le nom
  (le nom décide de la source remplacée).
- Le bouton « Importer » de la carte Données : `readImportedFile` gagne un
  cas `ImportedFile.Calendar(plan)` qui ouvre `CalendarImportDialog`.

### Décisions et pièges tracés

- **F-Droid n'interdit pas le réseau** (vérifié le 2026-10-24 sur
  l'[Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/) et
  les [Anti-Features](https://f-droid.org/en/docs/Anti-Features/)).
  - Exigé : code et chaîne de compilation libres, dépendances compilées
    depuis les sources, aucune bibliothèque propriétaire de pistage,
    publicité ou mesure d'audience. Une anti-fonctionnalité non déclarée
    entraîne un refus.
  - Pas interdit : `INTERNET`, comptes, OAuth.
  - Étiqueté, pas exclu : `NonFreeNet` (dépend d'un service propriétaire :
    un connecteur Microsoft seul), `TetheredNet` (service irremplaçable),
    `Tracking` (appel réseau sans accord ou actif par défaut).
  - L'absence de `INTERNET` est un **choix de Kairos**, pour n'avoir aucune
    anti-fonctionnalité (`plan-v3-kotlin.md` § Android, `fdroid/README.md`).
- **Décision du 2026-10-24 : pas de permission réseau sur Android, aucune
  connexion à un compte** (#139). Microsoft Graph demanderait une
  application Entra tenue par le projet ; beaucoup d'organisations
  bloquent le consentement des utilisateurs (« Need admin approval »), et
  le connecteur serait vraisemblablement étiqueté `NonFreeNet`. Le
  téléphone (étape 2) donne l'effet « compte connecté » : le réseau reste
  dans l'application de calendrier (Outlook, DAVx⁵, ICSx⁵). L'abonnement
  à un lien ICS sur le bureau seul, qui fait déjà un appel réseau
  (`mises-a-jour.md`), est différé.
- **Occurrences déployées plutôt que récurrences Kairos** : Kairos n'a que
  quotidien, jours ouvrés et hebdomadaire, sans exception (#108, #110),
  alors que les exports Outlook sont pleins d'exceptions. Coût : un créneau
  par occurrence (un point quotidien sur 6 mois ≈ 130 lignes),
  négligeable. Écarté : traduire les RRULE simples en `BlockRecurrence`
  (deux chemins, exceptions perdues).
- **Horizon de 6 mois** : couvre la vue Semaine et la future vue Mois
  (#107) ; borne les séries sans fin. Au-delà, on réimporte.
- **Fuseaux par les `VTIMEZONE` du fichier** : RFC 5545 impose un
  `VTIMEZONE` pour chaque `TZID` cité ; Outlook et Google les écrivent.
  Cela évite une table Windows → IANA et une base de fuseaux embarquée sur
  le web. Écartés : une table CLDR `windowsZones.xml` générée (à tenir à
  jour) et ical4j (JVM seulement, inutilisable en wasm). La conversion vers
  le fuseau de l'appareil passe par `TimeZone.currentSystemDefault()`, déjà
  utilisée par les écrans sur les trois plateformes. **Point à lever en
  premier** : sur wasmJs, vérifier que `TimeZone.of(<IANA>)` (repli 3)
  fonctionne avec kotlinx-datetime 0.8.0 sans base de fuseaux ; sinon, le
  repli 3 écarte l'événement (« fuseau horaire inconnu ») sur le web
  seulement.
- **Calendrier reconnu par son nom** : `FileService.openText` ne rend pas
  le nom du fichier, et un `.ics` n'a pas d'identifiant stable de
  calendrier. Le nom vient de `X-WR-CALNAME`, modifiable dans l'aperçu.
  Changer le nom crée un autre calendrier, ce que l'aperçu montre.
- **Réimport à partir d'aujourd'hui** : les créneaux passés sont la trace
  des journées vécues ; un nouvel export commence souvent à aujourd'hui et
  les effacerait.
- **Lecture seule** : un créneau modifié serait écrasé au réimport sans
  prévenir. La conversion en créneau manuel reste une piste.
- **Table `calendar_source` plutôt qu'un réglage** : les métadonnées
  (nom, masquage, date) appartiennent aux données importées, pas aux
  préférences ; `Settings` passe par le formulaire des Réglages.
  « Retirer » devient une suppression par clé.
- **Créneaux du téléphone non stockés** : ils changent dans l'application
  de calendrier ; une copie vieillirait et partirait dans l'export.
  `merge` au moment du calcul garde `core` pur et le dépôt inchangé.
- **Dédoublonnage par `uid` et début**, au profit du téléphone : c'est la
  source vivante. Sans début, une série entière disparaîtrait.
- **Journée entière = 00:00 → lendemain 00:00, un créneau par jour** : la
  vue Jour retient un créneau ponctuel qui chevauche la journée
  (`start < lendemain && end > début`, `DayView`) ; une fin au lendemain
  00:00 n'empiète pas sur le jour suivant. Le découpage à minuit garde la
  vue Semaine juste, elle qui range les créneaux par jour de début.
- **Provisoire et absent comptés occupés, « travaille ailleurs » libre** :
  ce que l'agenda d'Outlook montre comme indisponible.

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `ordonnancement.md` § Hors périmètre : la ligne « Événements de
  calendrier externes » renvoie à cette spec.
- `modele-donnees.md` : `TimeBlock.source`, `CalendarSource`, schéma 9
  (`8.sqm`).
- `export-import.md` : `calendarSources`, `sourceId`, `sourceUid` ; le
  bouton « Importer » reconnaît un fichier iCalendar ; sauvegarde
  `avant-import-calendrier-…`.
- `vue-jour.md`, `vue-semaine.md` : créneaux importés et du téléphone en
  lecture seule, icône et libellé.
- `equipe-moi.md` : les heures occupées de « moi » comptent les créneaux
  importés et du téléphone.
- `reglages.md` : carte « Calendriers » dans la catégorie Données.
- `architecture.md` : `core/ics`, `CalendarService`.
- `navigation-theme.md` : icône `CalendarMonth`.
- `distribution.md` (étape 2) : `READ_CALENDAR` dans le manifeste et dans
  « Comportement attendu ».
- `publication.md` et fiches Fastlane FR/EN (étape 2) : autorisation
  facultative de lecture du calendrier ; « sans réseau » reste vrai.
- `accueil.md` : aide de l'écran Réglages, si elle cite les cartes.
- `visite-guidee.md` : carte du catalogue (`FeatureCatalog`) ; `README.md`
  et `README.fr.md`.
