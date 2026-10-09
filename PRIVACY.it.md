# Informativa sulla privacy — App Manager Tech

_Traduzione della versione del 9 ottobre 2026._ · 🇫🇷 [Français](PRIVACY.fr.md) · 🇬🇧 [English](PRIVACY.md) · 🇩🇪 [Deutsch](PRIVACY.de.md) · 🇪🇸 [Español](PRIVACY.es.md)

> Questa traduzione è stata prodotta dallo sviluppatore con l'aiuto di strumenti automatici e non è
> ancora stata rivista da un madrelingua. **In caso di discordanza prevale la
> [versione francese](PRIVACY.fr.md).**

App Manager Tech (`com.filestech.appmanager`) è un gestore di app **interamente locale**: esamina
le app installate sul suo telefono, sul telefono stesso, e non invia nulla da nessuna parte.

## In breve

- **Nessun dato raccolto, nessun dato trasmesso.** L'app non dichiara l'autorizzazione `INTERNET`:
  è tecnicamente incapace di inviare qualsiasi cosa attraverso una rete. Un controllo automatico lo
  verifica sull'APK pubblicato stesso, a ogni build di integrazione continua.
- **Nessun account, nessuna registrazione, nessun identificativo.**
- **Nessuna pubblicità, nessun tracker, nessuno strumento di analisi**, nessuna segnalazione di
  crash inviata.
- **Nessun backup nel cloud**: `allowBackup=false`, e sia il database sia le impostazioni sono
  esclusi dai backup automatici di Android e dai trasferimenti da un dispositivo all'altro.

## Quali dati, e dove

L'app legge ciò che Android sa già delle app installate, e ne conserva una copia nel suo **spazio di
archiviazione privato**, a cui nessun'altra app ha accesso. Questo database non è cifrato: descrive
le sue app, non i suoi contenuti.

| Dato | Da dove proviene | Per quanto tempo |
|---|---|---|
| Elenco delle app: nome, versione, dimensioni, date di installazione, di aggiornamento e di ultimo utilizzo, store di origine, stato (attivata, ibernata) | Android (`PackageManager`, `StorageStatsManager`, `UsageStatsManager`) | Sostituito a ogni analisi |
| Tracker rilevati, punteggio privacy | Calcolati sul telefono (veda più avanti) | Ricalcolati a ogni visualizzazione |
| Storico delle autorizzazioni concesse a ciascuna app — **disattivato per impostazione predefinita** | Rilevazione periodica, se lo attiva | 90 giorni per impostazione predefinita, regolabile da 7 a 365 |
| Storico di installazioni, aggiornamenti e disinstallazioni, con l'impronta SHA-256 di ciascun APK e il motivo della disinstallazione che sceglie di indicare — **disattivato per impostazione predefinita** | Segnalazioni di Android ricevute mentre l'app è in esecuzione, se lo attiva | 180 giorni per impostazione predefinita, regolabile da 30 a 365 |
| Registro delle azioni avviate da App Manager Tech — **disattivato per impostazione predefinita** | Le sue azioni, se lo attiva | 180 giorni per impostazione predefinita, regolabile da 30 a 365 |
| Cestino e quarantene (con l'impronta SHA-256 di ogni APK salvato) | Le sue azioni | Finché non li svuota o non li ripristina |
| Impostazioni: tema, soglie, app ignorate o protette, tag, e l'autorizzazione di accesso alla cartella di backup che ha scelto | Lei | Fino alla disinstallazione |

**I tracker** vengono individuati confrontando i nomi dei componenti che ciascuna app dichiara ad
Android con un elenco integrato in App Manager Tech, tratto dal database pubblico di Exodus Privacy.
Questo elenco viene aggiornato insieme all'app; non viene mai scaricato.

Disinstallare App Manager Tech, o cancellarne i dati nelle impostazioni di Android, elimina tutto
quanto sopra. I file che **lei** ha fatto scrivere altrove (veda «Condivisione») restano dove li ha
messi.

Lo sviluppatore **non ha accesso** a questi dati e **non ne riceve alcuna copia**.

## Autorizzazioni richieste, e perché

Questo elenco è **completo**: sono le dodici autorizzazioni dichiarate nell'APK pubblicato, così come risultano dal relativo file manifest. Comprende quindi anche quelle che nessuna riga del nostro codice richiede,
ma che una libreria ha portato con sé. Un controllo automatico rifiuta ogni build il cui APK si
discosti da questo elenco (`tools/check-manifest-permissions.py`, eseguito a ogni build di
integrazione continua).

### Dichiarate dall'app

| Autorizzazione | Scopo | Rete? |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Vedere tutte le app installate: è la funzione stessa dell'app. | No |
| `PACKAGE_USAGE_STATS` | Accesso speciale «Accesso ai dati di utilizzo», che lei concede personalmente nelle impostazioni di Android. Fornisce le dimensioni e la data di ultimo utilizzo di ciascuna app. Se viene negato, le dimensioni risultano 0 e le app «Mai usata»; nient'altro smette di funzionare. | No |
| `GET_PACKAGE_SIZE` | Leggere le dimensioni delle app. | No |
| `REQUEST_DELETE_PACKAGES` | Aprire la finestra di disinstallazione di Android. È Android a chiedere conferma e a disinstallare. | No |
| `REQUEST_INSTALL_PACKAGES` | Ripristinare un'app messa in quarantena: consegnare l'APK salvato al programma di installazione di Android. Android richiede inoltre che sia lei ad autorizzare personalmente App Manager Tech a installare app, e chiede conferma a ogni installazione. Non serve a nient'altro. | No |
| `KILL_BACKGROUND_PROCESSES` | Arrestare i processi in background di un'app, su sua richiesta. | No |
| `POST_NOTIFICATIONS` | Tre notifiche facoltative: soglia della cache raggiunta, autorizzazioni modificate, fine di una quarantena. Viene richiesta quando ne attiva una (Android 13 e versioni successive) e può essere negata. | No |

### Portate dalle librerie utilizzate

| Autorizzazione | Proviene da | Cosa fa **realmente** qui | Rete? |
|---|---|---|---|
| `WAKE_LOCK` | `androidx.work` | Un wake lock viene acquisito brevemente durante l'esecuzione di un'attività in background: analisi automatica, rilevazione delle autorizzazioni, pulizia degli storici, promemoria della quarantena — quelle che ha attivato. | No |
| `RECEIVE_BOOT_COMPLETED` | `androidx.work` | Riprogrammare queste attività dopo un riavvio. | No |
| `FOREGROUND_SERVICE` | `androidx.work` | **Nulla.** `androidx.work` la dichiara per le attività «expedited»; l'app non ne programma nessuna. | No |
| `ACCESS_NETWORK_STATE` | `androidx.work` | **Nulla.** `androidx.work` la dichiara per le attività che attendono una rete; tutte quelle dell'app sono programmate senza alcuna condizione di rete. Permetterebbe soltanto di sapere se una rete è presente: senza `INTERNET`, è impossibile servirsene. | No |
| `com.filestech.appmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` | Autorizzazione **auto-concessa**, di livello «signature»: solo un'app firmata con la nostra chiave può ottenerla. Impedisce alle altre app di accedere al ricevitore che l'app registra per seguire le installazioni. | No |

L'app non chiede **mai** l'accesso a Internet, alla posizione, ai contatti, agli SMS, al calendario,
al microfono, alla fotocamera, né ai suoi file (`MANAGE_EXTERNAL_STORAGE`,
`READ_EXTERNAL_STORAGE`).

## Condivisione con terzi

**Nessuna.** Nessun dato viene condiviso, venduto o trasmesso a nessuno — l'app non ha alcun mezzo
tecnico per farlo.

Gli unici scambi possibili sono quelli che **lei** avvia, e restano sul suo telefono:

- **Esportazione (JSON, CSV, PDF)**: un rapporto sulle sue app, scritto nel percorso che sceglie nel
  selettore di file di Android. Rivela quali app sono installate sul suo telefono; ciò che accade
  poi al file dipende soltanto da lei, anche se lo salva in una cartella sincronizzata con un cloud.
- **Quarantena con backup**: l'app copia il file di installazione (APK) di un'app nella cartella che
  ha scelto, prima della sua disinstallazione. Un APK contiene l'app, **mai i suoi dati**. Questa
  cartella non viene cancellata quando disinstalla App Manager Tech.
- **Pagine web** («Cerca aggiornamenti», codice sorgente, segnalare un problema, licenza, questa
  informativa): l'app chiede al browser del suo telefono di aprire un indirizzo fisso. È il browser
  a collegarsi, secondo la propria informativa sulla privacy; App Manager Tech gli trasmette
  soltanto quell'indirizzo.
- **Schermate di Android** (disinstallazione, scheda di un'app, impostazioni): l'app trasmette loro
  il nome del pacchetto interessato, e nient'altro.

## Log

Le versioni pubblicate non scrivono **alcuna riga di log**: ogni messaggio viene scartato prima di
essere scritto. Solo le versioni di sviluppo ne scrivono, visibili unicamente tramite ADB.

## I suoi diritti (GDPR)

Poiché l'app non tratta alcun dato personale al di fuori del suo dispositivo, non esiste alcun trattamento remoto in relazione al quale esercitare i diritti di accesso, rettifica o cancellazione. Lei mantiene il pieno controllo:
disattivare uno storico, svuotare il cestino o disinstallare l'app cancella i dati corrispondenti
dal dispositivo. L'esportazione le fornisce una copia leggibile di ciò che l'app sa delle sue app.

## Minori

L'app non raccoglie alcun dato ed è adatta a tutti.

## Modifiche

Questa informativa può evolvere insieme all'app; la data in alto indica l'ultima revisione, e la
cronologia è pubblica in questo repository. La versione del 23 maggio 2026 ometteva gli storici e i
relativi tempi di conservazione, i file scritti al di fuori dell'app e metà delle autorizzazioni
dell'APK. Quella del 9 ottobre 2026 aggiunge `REQUEST_INSTALL_PACKAGES`, senza la quale Android
rifiutava qualsiasi ripristino da una quarantena.

## Editore e contatti

App Manager Tech è pubblicata da **Patrice Haltaya** (Francia), titolare del trattamento ai sensi
del GDPR — anche se, come spiegato sopra, nessun dato gli arriva mai. Contatto:
**contact@files-tech.com**.

Domande o segnalazioni: apra una [issue](https://github.com/gitubpatrice/APP-MANAGER-TECH/issues)
sul repository, oppure ci contatti tramite [files-tech.com](https://files-tech.com). Per la
sicurezza, veda [SECURITY.md](SECURITY.md).
