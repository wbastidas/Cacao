# Carpeta de fuentes

Aquí van las imágenes de entrenamiento, **una carpeta por origen** y dentro **una
carpeta por clase**. El contenido de esta carpeta no se sube al repositorio
(está en `.gitignore`) porque son miles de archivos pesados.

```
fuentes/
  fotos_app/                 ← exportadas desde CacaoTrace (Drive/CacaoTrace/Dataset/)
    sana/  monilia/  fitoftora/  otro/
  CocoaMoniliaDataSet/       ← dataset abierto
    h0/  m1/  m2/  m3/
  Cacao_Diseases_Pests/      ← Roboflow, CC BY 4.0
    HEALTHY/  FROSTYPOD/  BLACKPOD/  MIRID/
```

Después escribe el mapeo de clases (ver `mapeo_mazorca_ejemplo.json`) y corre
`preparar_dataset.py`. Revisa la licencia de cada dataset antes de usarlo: la
sección 8 del README de `entrenamiento/` explica cuáles permiten uso comercial.
