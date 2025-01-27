# What is this mod?
**TPA Utilities** is a **server-side mod** that adds **new commands** to your server!
This mod only adds **TPA (Ask for Teleportation) commands**.

# What new commands?
Here is the list :

- **/tpa [player_name]** : Send a teleport request to the player. If the player accepts, **you will be teleported to the player**. If the player don't respond after 60 seconds, the request expires.
- **/tpahere [player_name]** : Send a teleport request to the player. If the player accepts, **he will be teleported to you**. If the player don't respond after 60 seconds, the request expires. 
- **/tpaccept** : Accept the last teleport request that was sent to you.
- **/tpadeny** : Refuse the last teleport request that was sent to you.
- **/tpacancel** : Cancel all your teleport requests that are pending.
- **/tpalock** : Enable/Disable the ability of other players to send you teleport requests.
- **/tpalanguage [language]** : Change the language used by the mod for the player.

# What is the translations system?
With versions 1.1.0+ a new config file _"tpa_translations.json"_ is automatically generated on server start, you can add translations as below that your players can use with the **/tpalanguage** command (here i added a french translation):

```json
{
  "en": {
    "error_tpa_yourself": "Error : You can\u0027t tpa to yourself.",
    "error_tpa_disabled": "This player has disabled tpa.",
    "error_tpa_already_asked": "Error : You\u0027ve already asked to tpa to this player.",
    "wants_tpa_teleport": "%s wants to teleport to you! Accept with /tpaccept or click here!",
    "tpa_sent": "Your tpa request has been sent!",
    "tpa_expired": "Your tpa request has expired.",
    "error_tpahere_yourself": "Error : You can\u0027t tpahere to yourself.",
    "error_tpahere_disabled": "This player has disabled tpahere.",
    "error_tpahere_already_asked": "Error : You\u0027ve already asked to tpahere to this player.",
    "wants_tpahere_teleport": "%s wants you to teleport to him! Accept with /tpaccept or click here!",
    "tpa_here_sent": "Your tpahere request has been sent!",
    "tpahere_expired": "Your tpahere request has expired.",
    "teleport_success": "You have been successfully teleported!",
    "error_tpaccept": "Error : There is no tpa to accept.",
    "tpa_refused": "Your tpa request has been refused.",
    "tpahere_refused": "Your tpahere request has been refused.",
    "error_tpadeny": "Error : There is no tpa to deny.",
    "tpacancel_success": "All of your tpa and tpahere requests have been cancelled!",
    "tpalock_activated": "TPA lock activated!",
    "tpalock_deactivated": "TPA lock deactivated!",
    "tpalanguage_success": "TPA language changed!",
    "tpalanguage_failure": "Error : The language provided is invalid.",
    "version": "1.1"
  },
  "fr": {
    "error_tpa_yourself": "Erreur : Vous ne pouvez pas vous téléporter à vous-même.",
    "error_tpa_disabled": "Ce joueur a désactivé le tpa.",
    "error_tpa_already_asked": "Erreur : Vous avez déjà demandé à vous téléporter à ce joueur.",
    "wants_tpa_teleport": "%s veut se téléporter à vous ! Acceptez avec /tpaccept ou cliquez ici !",
    "tpa_sent": "Votre demande de téléportation a été envoyée !",
    "tpa_expired": "Votre demande de téléportation a expiré.",
    "error_tpahere_yourself": "Erreur : Vous ne pouvez pas vous téléporter à vous-même.",
    "error_tpahere_disabled": "Ce joueur a désactivé le tpahere.",
    "error_tpahere_already_asked": "Erreur : Vous avez déjà demandé à vous téléporter à ce joueur.",
    "wants_tpahere_teleport": "%s veut que vous vous téléportiez à lui ! Acceptez avec /tpaccept ou cliquez ici !",
    "tpa_here_sent": "Votre demande de tpahere a été envoyée !",
    "tpahere_expired": "Votre demande de tpahere a expiré.",
    "teleport_success": "Vous avez été téléporté avec succès !",
    "error_tpaccept": "Erreur : Il n'y a pas de demande de téléportation à accepter.",
    "tpa_refused": "Votre demande de téléportation a été refusée.",
    "tpahere_refused": "Votre demande de tpahere a été refusée.",
    "error_tpadeny": "Erreur : Il n'y a pas de demande de téléportation à refuser.",
    "tpacancel_success": "Toutes vos demandes de tpa et tpahere ont été annulées !",
    "tpalock_activated": "Verrouillage du TPA activé !",
    "tpalock_deactivated": "Verrouillage du TPA désactivé !",
    "tpalanguage_success": "Langue du TPA modifiée avec succès !",
    "tpalanguage_failure": "Erreur : La langue fournie est invalide.",
    "version": "1.1"
  }
}
```
