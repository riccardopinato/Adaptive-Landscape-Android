# Informativa tecnica sulla privacy — Adaptive Landscape Android (bozza di rilascio)

**Stato:** bozza operativa. La pubblicazione su Google Play richiede revisione finale e una informativa pubblica coerente con le impostazioni della Console.

### Dati elaborati localmente

L'app salva sul dispositivo (oppure nella memoria locale del browser per l'anteprima Web) solo preferenze di visualizzazione: tema, palette, intensità del movimento, numero di righe e colonne, coordinate normalizzate delle aree occupate, nomi dei profili scelti dall'utente. La copia negli appunti e l'importazione di un profilo JSON sono azioni esplicite dell'utente.

L'app **non richiede** registrazione, non accede alle app installate, non legge nomi o posizioni effettive delle icone del launcher, non cattura screenshot, non chiede accesso all'account e non invia le configurazioni a un server.

### Permessi e funzionalità Android

`WallpaperService` è registrato con il permesso di binding `android.permission.BIND_WALLPAPER`, gestito dal sistema operativo; non è un permesso di lettura delle app. Il codice attuale non dichiara permesso INTERNET, AccessibilityService o acquisizione schermo.

### Conservazione e cancellazione

I profili restano nella memoria locale dell'app. L'utente può eliminare i profili dall'interfaccia (mantenendone almeno uno). La cancellazione dei dati dell'app dalle impostazioni Android elimina le preferenze locali. Le azioni di copia/esportazione affidano all'utente la gestione del testo esportato.

### Materiale da verificare prima dello Store

Dichiarazione Data Safety Google Play, eventuali backup Android automatici della app, eventuali modifiche a dipendenze/analytics/pubblicità future, titolare del trattamento e contatto di privacy. Non presentare questa bozza come informativa definitiva.
