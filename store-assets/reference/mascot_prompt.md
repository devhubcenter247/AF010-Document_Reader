# Folio mascot — image prompt

Written from the vectors in the app (`app/src/main/res/drawable/ic_mascot_smile.xml`,
`ic_mascot_cheer.xml`, `ic_mascot_smile_ink.xml`). Colours are from `values/colors.xml`:
`primary_dark` #1858C4, `color_surface_card` #FFFFFF, `color_surface_variant` #E8EBF2,
`color_mascot_cheek` #FFB3C1.

## Base prompt

```
A cute, minimal mascot for a document reader app called "Folio": a single sheet of paper
standing upright with a folded top-right corner (dog-ear), rounded corners, white body
(#FFFFFF) with a thick royal-blue outline (#1858C4), the folded corner filled light grey-blue
(#E8EBF2). Simple kawaii face in the upper-middle: two small solid blue dot eyes, a small
curved smile, soft pink round cheeks (#FFB3C1, slightly transparent). A faint soft oval
shadow under it. Flat vector style, clean uniform line weight, no gradients, no texture,
no text, centered on a plain white background, friendly and calm, app-icon quality.
Aspect ratio 4:5.
```

## Poses (append to the base prompt)

- Cheer (Success screen): `arms raised in celebration, eyes closed happily (^ ^), small confetti around`
- Question / warning (dialogs): `head tilted slightly, one small arm raised, curious expression`
- No internet: `holding a broken plug, slightly worried face`
- Ink (dark outline variant): `outline and face in near-black ink instead of blue`
