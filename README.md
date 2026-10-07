# ULTRAKILL Revolver (Fabric 26.1.2)

Dépendances : Fabric Loader 0.19.3, Fabric API, GeckoLib 5.5.1.

- Modèle / animations / texture : src/main/resources/assets/ultrarevolver/{geckolib,textures}
- Position en main : "display" dans assets/ultrarevolver/models/item/revolver.json (à ajuster en jeu)
- Animations jouées : static_idle (boucle) et shoot (au tir). Les autres (draw, inspect, reload_*) sont dans le fichier, pas encore branchées.
- Les bras FPS (lefthand_pos / righthand_pos) ont été retirés du modèle.
- La texture rhino357_s.png (spéculaire TACZ) est copiée mais non utilisée.
