# Condizioni d'uso — App Manager Tech

_Traduzione della versione dell'8 ottobre 2026._ · 🇫🇷 [Français](TERMS.fr.md) · 🇬🇧 [English](TERMS.md) · 🇩🇪 [Deutsch](TERMS.de.md) · 🇪🇸 [Español](TERMS.es.md)

> Questa traduzione è stata prodotta dallo sviluppatore con l'aiuto di strumenti automatici e non è
> ancora stata rivista da un madrelingua. **In caso di discordanza prevale la
> [versione francese](TERMS.fr.md).**

## Editore

App Manager Tech (`com.filestech.appmanager`) fa parte della suite **Files Tech**, pubblicata da
**Patrice Haltaya**. Contatto: **contact@files-tech.com**.

## Licenza

App Manager Tech è software libero pubblicato con **licenza Apache 2.0**. Può usarlo, modificarlo e
ridistribuirlo alle condizioni di tale licenza. Il testo completo si trova nel file `LICENSE` del
repository dei sorgenti (https://github.com/gitubpatrice/APP-MANAGER-TECH). Fa fede soltanto il
testo inglese della licenza.

## Uso

L'app è messa a disposizione **gratuitamente** e fornita **così com'è, senza garanzia di alcun
tipo** (licenza Apache 2.0, sezione 7). La aiuta a esaminare le app installate sul suo telefono e a
intervenire su di esse; le decisioni che prende su questa base — disinstallare, disattivare, mettere
in quarantena — restano sue.

Usandola, conferma che il telefono le appartiene o che ha l'autorizzazione a gestirlo. Copiare il
file di installazione (APK) di un'app non le conferisce alcun diritto su di essa: non lo
ridistribuisca se la sua licenza lo vieta.

## Limitazioni

- **Le azioni passano per Android.** App Manager Tech non disinstalla, non disattiva e non svuota la
  cache di alcuna app da sé: apre la finestra di disinstallazione di Android, o la scheda dell'app
  nelle impostazioni di Android, ed è Android a chiedere conferma e ad agire. Non aggira mai queste
  conferme.
- **«Forza interruzione» interrompe soltanto i processi in background.** Android non consente a
  un'app di interromperne un'altra che sia visibile o che esegua un servizio in primo piano.
- **Il rilevamento dei tracker non è un audit di sicurezza.** Confronta i componenti che ciascuna
  app dichiara con un elenco fermo alla data della versione: può non rilevare un tracker assente da
  questo elenco, rinominato o integrato in altro modo, e segnalarne uno presente ma mai utilizzato.
- **Il punteggio privacy è una stima.** Viene calcolato a partire dalle autorizzazioni dichiarate,
  da alcuni accessi speciali e dalla fonte di installazione. Misura l'ampiezza degli accessi di
  un'app, non le sue intenzioni: un punteggio basso non indica un software dannoso, un punteggio
  alto non garantisce nulla.
- **L'avviso «app critica» non è esaustivo.** Prima di un'azione su un'app che riconosce come
  critica, o che lei ha protetto, l'app richiede una pressione prolungata di tre secondi. Non le
  riconosce tutte: disattivare o disinstallare un'app di sistema può rendere instabile il telefono.
- Senza l'«Accesso ai dati di utilizzo», le dimensioni risultano 0 e le app «Mai usata».
  Android può ritardare o bloccare le notifiche; l'app non può garantirne la consegna.

## Perdita di dati

- **Disinstallare un'app, o cancellarne i dati, è irreversibile.** Lo fa Android, dopo la propria
  conferma; App Manager Tech non ha alcun modo di annullarlo.
- **Il cestino non disinstalla nulla** finché non lo svuota: svuotarlo avvia la disinstallazione di
  ciascuna app che contiene.
- **Una quarantena con backup conserva soltanto l'APK.** I dati dell'app — account, preferenze,
  file — vanno persi alla sua disinstallazione, e ripristinarla reinstalla un'app vergine. Il
  ripristino passa per il programma di installazione di Android, che le chiede il consenso e, la
  prima volta, l'autorizzazione a installare app da App Manager Tech.
- **App Manager Tech non verifica ancora che un APK salvato non sia stato modificato** nella sua
  cartella. Ripristini soltanto file che ha salvato personalmente, in una cartella che nessun'altra
  app modifica.
- Disinstallare App Manager Tech ne cancella gli storici, il cestino e le quarantene. Gli APK
  salvati e le esportazioni restano nelle cartelle in cui li ha messi.

## Dati

Tutto ciò che l'app conserva resta **sul suo telefono** — veda
l'[informativa sulla privacy](PRIVACY.it.md). App Manager Tech non invia nulla su Internet e non ha
l'autorizzazione tecnica per farlo (nessuna autorizzazione Android `INTERNET`). Per la stessa
ragione, nessuno può disattivarla né rimuoverla dal suo telefono a distanza.

## Aggiornamenti

Gli aggiornamenti sono pubblicati sul repository GitHub ufficiale, firmati con la stessa chiave di
ogni versione precedente. L'app non si aggiorna mai da sola e non verifica mai la presenza di
aggiornamenti: il pulsante «Cerca aggiornamenti» apre la pagina delle versioni nel suo browser, e
installare una nuova versione spetta a lei.

## Responsabilità

Nei limiti consentiti dalla legge, l'editore non può essere ritenuto responsabile di alcun danno
diretto o indiretto derivante dall'uso dell'app (licenza Apache 2.0, sezione 8). In particolare,
**qualsiasi perdita di dati conseguente a una disinstallazione, a una cancellazione di dati, a una
disattivazione, a una quarantena o a un ripristino che lei ha avviato è di esclusiva responsabilità
dell'utente**. Nulla in queste condizioni limita una responsabilità che la legge non consente di
limitare.

## Legge applicabile

Queste condizioni sono regolate dalla **legge francese**, fatte salve le norme imperative di tutela
dei consumatori del suo paese di residenza. Ove la legge lo consenta, in caso di controversia sono
competenti i tribunali francesi.

## Modifiche

Queste condizioni possono evolvere insieme all'app; la data in alto indica l'ultima revisione, e la
cronologia è pubblica in questo repository. La versione del 23 maggio 2026 annunciava una
distribuzione tramite F-Droid: era falso, App Manager Tech non vi è pubblicata.

## Contatti

**contact@files-tech.com** · [issue del repository](https://github.com/gitubpatrice/APP-MANAGER-TECH/issues)
· [files-tech.com](https://files-tech.com/app-manager-tech.php)
