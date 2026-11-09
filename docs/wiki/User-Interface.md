# User Interface

All Odyssey menus are server-side chests: they work with an unmodded client. Items shown as buttons can
never be taken, and every action is checked on the server.

## Hub (`/odyssey`)
![Hub: top row with the player's head in the middle, a sunflower showing the Points balance on the left and a chest minecart (Mailbox) on the right](images/library-hub.png)

- **Top row**: your Odyssey Points (sunflower) and Mailbox (glows when you have new mail)
- **Middle**: one icon per installed Odyssey mod; more pages appear when more mods are added
- **Bottom row** (admins only): Modules, Settings, Audit log, Performance

## Mailbox
![Mailbox: paged list of mail items, each showing sender, date and contents in its tooltip; "Claim all" hopper in the bottom row](images/library-mailbox.png)

Click a mail to claim it. If your inventory is full, whatever does not fit stays in the mailbox.

## Staff: send mail
![Send mail screen: four empty rows for items, bottom row with recipient head, Points sunflower, title name tag and green Send button](images/library-mail-compose.png)

Put items in the top four rows, set points and a title, pick a player (left-click the head) or everyone
(right-click), then **Send**. Each recipient gets a copy. Leaving without sending gives your items back.

## Settings editor
![Settings editor: paged list of a config's settings, booleans as lime/grey dye, numbers as clocks, text as name tags](images/library-settings.png)

Click a setting to toggle or edit it. Changes apply instantly.

## Modules
![Modules screen: one toggle per module, lime dye for enabled, grey dye for disabled](images/library-modules.png)

## Inventory backups (`/odyssey snapshots <player>`)
![Snapshots screen: ender chests for active backups and chests for history, tooltip with time, owner mod and contents](images/library-snapshots.png)

Restoring always saves the player's current inventory first, so a restore can never delete or duplicate items.
