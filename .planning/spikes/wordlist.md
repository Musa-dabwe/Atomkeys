# Spike 0.5 — Word list sourcing & license

**Status:** answered
**Date:** 2026-08-06
**Gates:** Phase 6.1 (dictionary), Phase 6.2 (candidate scoring)

---

## Decision

Use **FrequencyWords** (HermitDave) — `content/2018/en/en_50k.txt`.

## License — cleared ✓

**MIT License**, Copyright (c) 2016 Hermit Dave. Verified by fetching
`https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/LICENSE`.

MIT requires the copyright notice and permission notice be included in "all copies or substantial
portions of the Software."

- [ ] **Action for Phase 6.1:** ship the MIT notice alongside the asset — either as
      `app/src/main/assets/dictionary/LICENSE` or in an in-app "Open source licenses" screen.
      This is a real obligation, not a formality.

**Upstream corpus:** the frequency data derives from **OpenSubtitles**. That provenance drives every
caveat below.

## Measurements

Raw `en_50k.txt`: **609 KB**, 50,000 entries, format `word<space>count`, **sorted by count descending**.

After cleaning (`^[a-z]+$`, length 2–19, plus `a` and `i` as the only permitted single letters):

| Metric | Value |
|---|---|
| Entries retained | **46,693** (93.4%) |
| Plain text | 568 KB |
| gzip -9, words + counts | 251 KB |
| **gzip -9, words only** | **177 KB** |

### Junk removed, by class

| Class | Count | Example |
|---|---|---|
| Contains non-`a-z` | 3,283 | `hyeon-to`, `buddy-buddy` |
| Leading apostrophe (tokenizer artifacts) | 300 | `'s`, `'t`, `'re` |
| Single char other than `a`/`i` | 24 | `q`, `z` |
| Length ≥ 20 | 4 | — |

The `'s` / `'t` entries are OpenSubtitles tokenization splitting contractions. They rank absurdly
high (`'s` is #6 overall) and would poison correction if kept. Dropping them means **contractions
must be handled as their own concern** in Phase 6 — `don't`, `it's`, `I'm` need to be reachable, and
this list does not supply them intact.

- [ ] **Action for Phase 6.1:** add a small hand-written contractions list. Do not try to recover
      them from this corpus.

## Key finding — frequencies do not need to be stored

The list is already sorted by descending frequency, so **line index is the frequency rank**. Storing
counts is redundant: drop them, keep sort order, and recover a score from rank via a Zipf
approximation (`p ∝ 1/rank^s`) or a quantized log-bucket byte if a real weight is wanted.

This is the difference between **251 KB and 177 KB** gzipped, and it simplifies the asset to a
newline-delimited word list. Take it.

## Quality findings

### Coverage is adequate ✓

Spot-checked formal/business vocabulary that a subtitle corpus might plausibly miss — all present at
sensible ranks:

| Word | Rank | Word | Rank |
|---|---|---|---|
| schedule | 2,397 | deadline | 6,938 |
| colleague | 3,813 | algorithm | 11,181 |
| receipt | 5,644 | appendix | 13,718 |
| prescription | 6,031 | invoice | 17,752 |
| mortgage | 6,636 | logistics | 19,822 |
| | | authentication | 40,105 |

Modern technical jargon is absent (`kubernetes` → MISSING), as expected from a 2018 subtitle corpus.
This is precisely what auto-learn (6.3) exists to cover, so it is not a defect.

### Profanity ranks dangerously high ↯ risk

| Word | Rank |
|---|---|
| shit | 277 |
| fuck | 291 |
| damn | 388 |
| bitch | 602 |

Conversational corpus, so profanity is genuinely frequent. At rank ~280 these **outrank most
legitimate correction targets**, meaning a near-miss typo can get auto-corrected *into* profanity.
That is the single worst failure mode a keyboard has — it happens in someone's work chat.

- [ ] **Action for Phase 6.2:** maintain a blocklist that profanity may **never be an auto-correct
      *target***. It must remain typeable literally, and may be offered as an explicit candidate
      chip when the user clearly typed it — but the composing region must never silently become it.
- [ ] Add profanity-substitution cases to the golden corpus (6.4) as **regression tests**, so this
      cannot quietly break later.

### Other caveats

- **Register skew.** Subtitle English is spoken/informal. Good for messaging, mildly off for email
  and documents. Acceptable for v1; revisit if dogfooding (9.3) shows it.
- **Proper nouns and character names** leak in from subtitles. Not filtered — they mostly behave as
  harmless low-rank entries, but check any that rank high.
- **No case information.** Everything is lowercased, so `i` → `I` capitalization and proper-noun
  casing are entirely Phase 4.4's job (`getCursorCapsMode`), not the dictionary's.

## Independence note

The golden typo corpus (6.4) is **unrelated to this choice** and must still be hand-built from real
typing. Nothing here substitutes for it.

## Reproducing the measurements

```sh
curl -sSL -o en_50k.txt \
  https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt

# clean → words-only, frequency order preserved
awk '{print $1}' en_50k.txt \
  | grep -E '^[a-z]+$' \
  | awk 'length($0)>=2 || $0=="a" || $0=="i"' \
  | awk 'length($0)<20' > words.txt

wc -l words.txt                    # 46693
gzip -9 -c words.txt | wc -c       # ~181k bytes
```
