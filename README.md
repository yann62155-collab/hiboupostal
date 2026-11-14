# Hibou Postal — NeoForge 21.1.215 / Minecraft 1.21.1

Un système de hiboux postaux médiéval : chaque joueur reçoit un **numéro de hibou** unique et peut envoyer des **lettres scellées** aux autres sujets du royaume.

- Mod ID : `hiboupostal`
- Minecraft 1.21.1, NeoForge **21.1.215** (exactement), Java 21
- Mod à installer **chez le serveur et chez tous les joueurs** (il ajoute des items et des menus).

## Compiler

```bash
./gradlew build          # Windows : gradlew.bat build
```
Le JAR est produit dans `build/libs/hiboupostal-1.2.0.jar`.
Pour tester en jeu : `./gradlew runClient` (ou `runServer`).

## Fonctionnement

| Élément | Détail |
|---|---|
| **Numéro de hibou** | Attribué automatiquement à la première connexion (n°0001, n°0002…). Il ne change jamais. **Il est secret** : personne ne voit la liste des numéros. |
| **Carnet de contacts** | La volière n'affiche que **vos contacts**. Pour ajouter quelqu'un, il faut connaître son numéro : `/hibou sceau <joueur>` le lui tend (joueur à moins de 8 blocs), puis `/hibou ajouter <numéro> [surnom]` ou le bouton « ★ Ajouter » de la volière, ou « ★ Carnet » sur une lettre reçue. On peut écrire à un numéro hors carnet si on le connaît. |
| **Beuglante** | Lettre hurlante. Beuglante vierge = lettre vierge + poudre à canon + colorant rouge (ou 1 papier + 1 poudre à canon directement). Onglet « ‼ Beuglante » de la volière : un **cri écrit** (100 caractères) + une **voix enregistrée au micro** (facultative, 20 s max). À l'arrivée, elle éclate au-dessus du destinataire : titre géant, cri dans le chat de tous les joueurs à 24 blocs, voix jouée chez les joueurs PC, fumée et hurlement. Non ouverte, elle éclate d'elle-même au bout de 60 s. Jamais déposée en boîte aux lettres : elle attend le retour du destinataire. |
| **Joueurs Xbox / console** | Détection automatique (client sans le mod). Volière = coffre vanilla (carnet en têtes de joueurs + boutons), lettres envoyées avec un **livre et plume** (`/hibou envoyer`), lettres reçues sous forme de **livre écrit**, colis via un coffre d'une rangée (carton ou coffre vanilla consommé), cartons reçus déballés directement, boîte aux lettres en coffre vanilla (retrait seul), beuglantes par `/hibou beugler` (cri écrit seulement). |
| **Lettre vierge** | Papier + plume (sans forme) → 2 lettres vierges. Chaque envoi en consomme une. Clic droit avec une lettre vierge ouvre aussi la volière. |
| **Volière (menu)** | Registre des hiboux à gauche (joueurs présents en premier, ● vert = connecté), cliquer un nom remplit le destinataire. À droite : destinataire (n° ou pseudo), objet, missive. |
| **Envoi** | Le hibou s'envole (son + particules), message privé à l'expéditeur. Après le temps de vol, la **lettre scellée** arrive dans l'inventaire du destinataire (ou à ses pieds si son inventaire est plein). |
| **Destinataire hors-ligne** | Le hibou attend : la lettre est remise à sa prochaine connexion (désactivable). |
| **Notifications** | Messages privés dans le chat, style médiéval, avec un bouton cliquable **[Répondre]**. L'expéditeur est prévenu quand la lettre a été remise. |
| **Carton (colis)** | 4 papiers + 1 ficelle (sans forme) → 1 carton. `/hibou colis`, clic droit avec un carton ou le bouton « Envoyer un colis » de la volière ouvre le comptoir d'expédition : **5 emplacements** + destinataire + petit mot facultatif. Chaque envoi consomme un carton vide de l'inventaire. |
| **Carton scellé** | Livré par un hibou grand-duc. Son contenu et l'expéditeur s'affichent au survol ; clic droit pour l'ouvrir (le contenu va dans l'inventaire, le surplus tombe à terre). |
| **Boîte aux lettres** | Bloc craftable (3 planches, 1 coffre au centre, 3 planches autour, 1 bâton en bas). Elle appartient à celui qui la pose : lui seul peut l'ouvrir et la casser. 27 emplacements, retrait seul. |
| **Quand le courrier y arrive** | Destinataire **déconnecté** → déposé dans sa boîte (s'il en a une). Destinataire **connecté mais besace pleine** → déposé dans sa boîte. Sinon → directement dans l'inventaire. Lettres et cartons suivent la même règle. |
| **Avertissements** | À la connexion : « Votre boîte aux lettres contient 2 missives et 1 carton ». Besace pleine : message immédiat indiquant que l'envoi est dans la boîte. L'expéditeur est prévenu de l'endroit où son envoi a été déposé. |
| **Sans boîte / boîte pleine** | Hors-ligne : le hibou attend la reconnexion. En ligne avec besace pleine : l'envoi est déposé aux pieds du joueur. |
| **Lecture** | Clic droit sur la lettre scellée : parchemin avec expéditeur, numéro de hibou, date, objet, texte défilant et bouton **Répondre**. |

## Commandes (aucune permission requise)

| Commande | Effet |
|---|---|
| `/hibou` | Ouvre la volière |
| `/hibou numero` | Affiche votre numéro de hibou (secret) |
| `/hibou sceau <joueur>` | Tend votre numéro à un joueur proche |
| `/hibou ajouter <numéro> [surnom]` | Inscrit un contact dans votre carnet |
| `/hibou retirer <contact>` | Raye un contact |
| `/hibou contacts` | Liste votre carnet |
| `/hibou envoyer <contact> [objet]` | Envoie le livre et plume tenu en main comme lettre (PC et Xbox) |
| `/hibou ecrire <contact>` | Ouvre la volière avec le destinataire prérempli |
| `/hibou colis <contact>` | Expédie un carton (5 emplacements) |
| `/hibou beugler <contact> <cri>` | Envoie une beuglante (cri écrit) |
| `/hibou aide` | Aide en jeu |
| `/hibou chercher <pseudo>` | **Admin** : numéro de hibou d'un joueur |
| `/hibou console` | **Admin** : voir la volière comme un joueur Xbox (pour tester) |

Le destinataire accepte : un numéro (`7`, `0007`, `#7`, `n°7`) ou le surnom / pseudo d'un contact **de votre carnet**. Un pseudo hors carnet est refusé.

## Configuration serveur

Fichier `world/serverconfig/hiboupostal-server.toml` :

| Clé | Défaut | Rôle |
|---|---|---|
| `deliveryDelaySeconds` | 5 | Temps de vol du hibou |
| `sendCooldownSeconds` | 10 | Délai entre deux envois d'un même joueur |
| `allowOfflineDelivery` | true | Autoriser l'envoi aux joueurs hors-ligne |
| `maxTitleLength` | 40 | Longueur max de l'objet |
| `maxBodyLength` | 1000 | Longueur max de la lettre |
| `maxContacts` | 100 | Taille max du carnet |
| `sealRange` | 8 | Distance max pour `/hibou sceau` |
| `beuglanteMaxSeconds` | 20 | Durée max de la voix d'une beuglante |
| `beuglanteRange` | 24 | Distance à laquelle on entend une beuglante |
| `beuglanteAutoOpenSeconds` | 60 | Délai avant qu'une beuglante non ouverte éclate (0 = jamais) |

## Sauvegarde

`world/data/hiboupostal.dat` : registre des hiboux (UUID → numéro, dernier pseudo), envois en vol, contenu des boîtes aux lettres et positions des boîtes posées.
Le contenu d'une boîte est lié au joueur, pas au bloc : casser la boîte ne fait rien perdre, le courrier se retrouve dans la prochaine boîte posée.
Les voix des beuglantes : `world/hiboupostal/beuglantes/*.ulaw`, supprimées dès que la beuglante a éclaté.
Les lettres reçues sont des items : leur contenu est stocké dans l'item (data component `hiboupostal:letter`).

## Sécurité côté serveur

Tout est validé par le serveur : présence d'une lettre vierge ou d'un carton, contenu du carton lu dans le menu serveur (le client ne peut pas inventer d'objets), objets rendus si le menu est fermé sans envoi, pas de carton scellé dans un carton, longueurs, codes couleur `§` retirés, pas d'envoi à soi-même, anti-spam par délai. Le client ne peut pas créer de lettre scellée lui-même.

## Structure

```
fr.citizen.hiboupostal
├── HibouPostal.java        point d'entrée, événements
├── Config.java             configuration serveur
├── item/                   lettres, cartons, data components
├── menu/                   menu du comptoir d'expédition (5 emplacements)
├── block/                  bloc Boîte aux lettres + bloc-entité (propriétaire)
├── data/OwlPostData.java   SavedData (registre, envois en vol, boîtes aux lettres)
├── server/                 logique d'envoi/remise, commandes, messages
├── network/                paquets réseau + pont client
└── client/                 écrans Volière, Lecture et Expédition (parchemin)
```
