Place MobileNetV3 image embedder model here as:

  mobilenet_v3_embedder.tflite

The app will fall back (always-accept) if the model is missing, but cosine similarity gating
is enabled only when the model is present.

