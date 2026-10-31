# Artwork changes — 0.17.1

Edited with the built-in image_gen tool using the original TV Vault artwork.
Final project assets:
- app/src/main/res/drawable-nodpi/tv_vault_icon.png (512x512; alpha corners)
- app/src/main/res/drawable-nodpi/tv_vault_banner.png (320x180; full-bleed)
- Fastlane images updated in both languages.

Icon prompt: Change ONLY the opaque near-black corners outside the existing
rounded square tile into genuine transparent alpha. Preserve the blue shield,
padlock, colors and glow; no text or new elements.

Banner prompt: Preserve the metallic blue shield and silver padlock with the
charcoal textured background and overhead light. Extend background to every
edge, remove top/bottom letterbox bars, compose shield left and large readable
white/silver TV Vault lettering right, 16:9. Exact text: TV Vault. No tagline.

Technical downscaling preserves alpha and fits standard Android TV dimensions.
