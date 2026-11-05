# Ingot Labs Base

Plugin ekonomii serwerowej dla Paper (1.21.1 i nowsze). Własne konta bankowe, przelewy, ranking najbogatszych,
panel GUI oraz panel administratora. Rejestruje się jako provider ekonomii **Vault**, więc inne pluginy
(sklepy, claimy, EssentialsX itd.) korzystają z niego bez zmian.

**Wersja:** 0.1.3 (Alpha)

## Wymagania
- Paper 1.21.1+ (Java 21 lub nowsza)
- Vault lub VaultUnlocked
- Opcjonalnie: PlaceholderAPI

## Budowanie
```
gradle build
```
Gotowy plik: `build/libs/IngotLabsBase-0.1.3.jar`

## Menu (`/bank`)
- **Twoje konto** - otwiera osobne menu ze stanem konta i pozycją w rankingu.
- **Najbogatsi gracze** - ranking jako główki; najedź na główkę, aby zobaczyć stan konta, miejsce, udział w pieniądzach serwera i różnicę do sąsiada.
- **Wyślij pieniądze** - krok 1: wybór gracza (główki graczy online lub nick wpisany na czacie), krok 2: kwota przyciskami +/- albo wpisana na czacie, potem potwierdzenie.
- **Historia transakcji** - wpisy jako przedmioty (przy przelewach główka drugiej strony); najedź, aby zobaczyć szczegóły.
- **Konto giełdowe** - otwiera konto z WebMarket (`exchange` w `config.yml`; ustaw tam właściwą komendę).
- **Panel administratora** - tylko dla osób z uprawnieniem `ingotlabs.admin.panel`.

`/pay` bez argumentów otwiera wybór gracza, `/pay <gracz>` od razu wybór kwoty.

## Przejmowanie komend
Komendy `/balance`, `/bal`, `/money`, `/pay`, `/baltop`, `/balancetop`, `/eco`, `/economy` (oraz warianty `e...`)
są przejmowane od EssentialsX i innych pluginów po starcie serwera. Lista jest w `config.yml`
(`commands.takeover`), całość wyłączysz przez `commands.override-other-plugins: false`.
Oryginalne komendy wracają po wyłączeniu pluginu.

## Komendy i permisje
| Komenda | Permisja | Domyślnie |
|---|---|---|
| `/bank` | `ingotlabs.bank` | wszyscy |
| `/balance [gracz]` (`/bal`, `/money`) | `ingotlabs.balance`, `ingotlabs.balance.others` | wszyscy / OP |
| `/pay <gracz> <kwota>` | `ingotlabs.pay`, `ingotlabs.pay.bypass-cooldown` | wszyscy / OP |
| `/baltop [strona]` | `ingotlabs.top` | wszyscy |
| `/ieco give/take/set <gracz> <kwota>` | `ingotlabs.admin.give/take/set` | OP |
| `/ieco reset <gracz>` | `ingotlabs.admin.reset` | OP |
| `/ieco check <gracz>` | `ingotlabs.admin.check` | OP |
| `/ieco history <gracz> [strona]` | `ingotlabs.admin.history` | OP |
| `/ieco panel` | `ingotlabs.admin.panel` | OP |
| `/ieco reload` | `ingotlabs.admin.reload` | OP |
| `/ingotlabs reload` (`/ilb`, `/ibase`) | `ingotlabs.admin.reload` | OP |

`ingotlabs.admin` nadaje wszystkie uprawnienia administracyjne.
Kwoty przyjmują przyrostki `k`, `m`, `b`, `t` (np. `1.5k`).

## Język
W `config.yml` ustaw `language: en` albo `language: pl`. Pliki znajdują się w `plugins/IngotLabsBase/lang/`
i można je dowolnie edytować (MiniMessage). Dla języka polskiego dochodzą aliasy komend: `/saldo`, `/przelew`,
`/ranking`, `/ekonomia`, `/konto` (sekcja `commands` w pliku językowym, zmiana wymaga restartu).

## PlaceholderAPI
- `%ingot_balance%`, `%ingot_balance_formatted%`, `%ingot_balance_commas%`, `%ingot_balance_short%`
- `%ingot_rank%`
- `%ingot_top_N_name%`, `%ingot_top_N_balance%`, `%ingot_top_N_balance_formatted%`, `%ingot_top_N_balance_short%`
- `%ingot_currency_symbol%`, `%ingot_currency_name%`, `%ingot_currency_name_plural%`

## API dla innych pluginów (np. WebMarket)
```java
IngotAPI api = IngotAPI.get();
api.deposit(uuid, new BigDecimal("50"), "WebMarket sell");
api.transfer(from, to, new BigDecimal("10"), "reason");
```
Operacje są atomowe i bezpieczne wątkowo. Każda zmiana salda trafia do historii transakcji.

## Jak to działa
- Wszystkie konta są trzymane w pamięci, a zapis do bazy (SQLite lub MySQL) odbywa się asynchronicznie.
- Pieniądze są liczbami całkowitymi (4 miejsca po przecinku wewnętrznie), nigdy `double`.
- Baza ma wersjonowanie schematu (tabela `meta`) pod przyszłe migracje.

## Znane ograniczenia (0.1.0)
- Brak wsparcia Folia.
- Jeden serwer na jedną bazę (konta są cache'owane w pamięci, więc współdzielenie bazy między serwerami nie jest jeszcze obsługiwane).
- Brak banków Vault (zwracają NOT_IMPLEMENTED), brak wielu walut, brak importu z EssentialsX.
- Zmiana `currency.decimals` przy istniejących saldach nie jest zalecana.
