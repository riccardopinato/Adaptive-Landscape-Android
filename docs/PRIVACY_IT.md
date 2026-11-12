# Informativa tecnica sulla privacy — Adaptive Landscape Android (bozza di rilascio)

**Stato:** bozza operativa. La pubblicazione su Google Play richiede revisione finale e una informativa pubblica coerente con le impostazioni della Console.

### Dati elaborati localmente

L'app salva sul dispositivo (oppure nella memoria locale del browser per l'anteprima Web) solo preferenze di visualizzazione: tema, palette, intensità del movimento, numero di righe e colonne, coordinate normalizzate delle aree occupate, nomi dei profili scelti dall'utente. La copia negli appunti e l'importazione di un profilo JSON sono azioni esplicite dell'utente.

L'app **non richiede** registrazione, non cattura automaticamente screenshot, non legge contenuti di altre applicazioni, non chiede accesso all'account e non invia configurazioni a un server. **Eccezione esplicita — Home Deskland opzionale:** se l'utente sceglie Deskland come launcher, la Home interroga localmente le attività avviabili e i relativi nomi/icone tramite `LauncherApps`, per mostrare l'elenco delle app e aprirle. Memorizza nella memoria privata locale gli identificativi delle app posizionate sulla propria Home, le coordinate, le cartelle, le preferenze dock e gli ID dei widget autorizzati. Non legge la disposizione privata delle icone dei launcher Xiaomi, Samsung o Pixel e non trasmette tali dati in rete.

### Permessi e funzionalità Android

`WallpaperService` è registrato con il permesso di binding `android.permission.BIND_WALLPAPER`, gestito dal sistema operativo; non è un permesso di lettura delle app. Il codice attuale non dichiara permesso INTERNET, AccessibilityService o acquisizione schermo.

### Conservazione e cancellazione

I profili restano nella memoria locale dell'app. L'utente può eliminare i profili dall'interfaccia (mantenendone almeno uno). La cancellazione dei dati dell'app dalle impostazioni Android elimina le preferenze locali. Le azioni di copia/esportazione affidano all'utente la gestione del testo esportato.

### Materiale da verificare prima dello Store

Dichiarazione Data Safety Google Play, eventuali backup Android automatici della app, eventuali modifiche a dipendenze/analytics/pubblicità future, titolare del trattamento e contatto di privacy. Non presentare questa bozza come informativa definitiva.


### v0.4.2 — Foto marina scelta dall'utente

Su Android l'utente può selezionare volontariamente una fotografia del mare
tramite il selettore file di sistema. Il file viene validato e ricodificato
come JPEG per rimuovere i metadati, poi conservato esclusivamente nella
cartella privata `noBackupFilesDir` dell'app, esclusa dal normale backup
automatico Android. La fotografia non viene caricata su alcun server, non
viene inserita nell'esportazione dei profili e non richiede permessi di
accesso generale alla galleria. È condivisa localmente tra i profili
Spiaggia, sostituibile ed eliminabile mediante il comando «Rimuovi foto».
La disinstallazione/cancellazione dati rimuove anche la copia privata.

La fotografia **non è** lo screenshot Home usato solo temporaneamente per
calibrare le icone: quest'ultimo continua a non essere conservato.
Verificare fisicamente anche il comportamento su backup personalizzati OEM
prima di pubblicare sullo Store.


### Modalità Home adattiva sperimentale (v0.5.1)

La selezione del launcher predefinito e la conferma dello sfondo animato avvengono separatamente nelle schermate di sistema Android; l'app non si attribuisce autonomamente i relativi privilegi. Le preferenze di Home (`auto_home_icons_v1`, `auto_home_folders_v1`, `auto_home_dock_v1`, `auto_home_widgets_v1`) rimangono locali e non contengono testi dei widget o contenuti delle app. La funzione è sperimentale: la compatibilità dei provider widget e l'eventuale backup automatico dei dati richiedono una verifica prima dello Store. Il renderer opera su un thread dedicato per ridurre il carico sul thread principale; questo dettaglio non modifica la natura dei dati trattati.
