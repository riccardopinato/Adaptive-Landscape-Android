# Launcher/Dock — audit comparativo OpenTaskbar e lumabar (16/11/2026)

**Stato:** documentazione di ricerca, **nessun runtime modificato**. Factory `App-Factory-Core@c5adc147046beb7024629466d10eeb799c3c2619`. La stabilizzazione del launcher Android e i test fisici restano prioritari.

## Referenze immutabili

| Fonte | Commit | Licenza/ruolo |
|---|---|---|
| [OpenTaskbar](https://github.com/alztrk/opentaskbar/tree/6348d7519f83e032c31a065d72ad3b327906da15) | `6348d7519f83e032c31a065d72ad3b327906da15` | MIT; codice **Windows WPF** (non Android). Studio `DockAutoHideController.cs`, `TaskbarReplacementSession.cs`, `WindowThumbnailPreview.cs`, `Design/README.md` |
| [lumabar](https://github.com/Acadbek/lumabar/tree/5929d128d617b7d86165f74fa74bfc3fbcfbed32) / [sito](https://lumabar.xyz/) | `5929d128d617b7d86165f74fa74bfc3fbcfbed32` | Prodotto Windows commerciale; repository documentale, **source unavailable**. Concept reference, no copying |

## Confronto con Deskland Android

1. **P0 sicurezza launcher:** OpenTaskbar conserva stato della taskbar Windows e lo ripristina anche su errore. In Android serve percorso esplicito **Apri impostazioni Home predefinita / Torna al launcher precedente**, senza modificare silenziosamente default e con onboarding reversibile. Il journal Windows non va copiato.
2. **P0 funzionalità base:** drawer ricercabile, app pinnate, ordine persistente, dock stabile, multi-home, widget Android reali. Prendere l'obiettivo UX, non API DWM/Jump List/Win32.
3. **P1 gesti solidi:** tap per aprire; long-press per drag; spostamento senza ricreare continuamente la gerarchia delle View; drag annullabile; stato di focus/TalkBack.
4. **P1 visibilità contestuale:** pattern IntelliHide → dock visibile finché utile, mai ostacolare finestre o gesture Android; limitare callback e polling, sospendere animazioni quando invisibile.
5. **P2 look and feel:** pill flottante, running/active dot, animazioni contenute, superfici neutral/glass leggibili; non clonare identità, screenshot o Liquid Glass di lumabar.
6. **P2 informazioni in un colpo d'occhio:** eventuali piccoli widget contestuali (meteo, prossima attività, stato dispositivo) soltanto dopo le funzionalità Home P0; usare AppWidgetHost reale con permessi, senza inventare API/provider o elaborare dati sensibili senza consenso.

## Trasferibilità tecnica (da non confondere)

- **Trasferibile come requisito:** recoverability, state machine di visibilità, token di motion e leggibilità, persist/reorder, interazione contestuale.
- **Non trasferibile direttamente:** `DwmRegisterThumbnail`, `SHAppBarMessage`, Win32 window hooks, taskbar state registry, WPF/XAML, provider widget commerciali.
- **Android:** Activity `HOME`, `LauncherApps`, `AppWidgetHost`, lifecycle, gesture/insets, compatibilità OEM e battery policies richiedono soluzioni native autonome.
- **Licenze:** OpenTaskbar MIT compatibile *per i file applicabili* e attribuzione; lumabar source non pubblicato → nessuna copia codice o asset; Deskland Windows originale resta GPL-3.0 **solo reference**, secondo `AGENTS.md`.

## Acceptance gate prima di implementazione

- Nessun nuovo lavoro deve dichiarare Home certificata prima di drawer/app launch/search/drag/restore testati.
- Verificare tap vs long press, rotazione/insets, crash recovery, riavvio, app install/remove, TalkBack, test su un Redmi/Xiaomi e un secondo Android.
- Provare durata frame/batteria per render in background, soprattutto il mare animato, e rispettare pause when invisible.
- Registrare sorgente e ref nel ledger, usare branch/PR, test exact-head e QA fisico separato.
- La nuova estetica **non deve rallentare** la risoluzione dei P0 già noti.

Rimandi: [audit Deskland preesistente](ROADMAP.md), [provenienza](REUSE_PROVENANCE.md), [Catalogo Design-App](https://github.com/riccardopinato/Design-App).
