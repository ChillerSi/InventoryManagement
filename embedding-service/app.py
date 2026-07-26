import io
import os
from fastapi import FastAPI, UploadFile, File
from PIL import Image, ImageOps
import torch
from transformers import AutoModel, AutoProcessor

MODEL = os.getenv("SIGLIP_MODEL", "google/siglip-base-patch16-224")
MODEL_REVISION = os.getenv("SIGLIP_MODEL_REVISION", "7fd15f0689c79d79e38b1c2e2e2370a7bf2761ed")
MODEL_VERSION = f"{MODEL}@{MODEL_REVISION}"
app = FastAPI(title="义采通 SigLIP Service")
processor = AutoProcessor.from_pretrained(MODEL, revision=MODEL_REVISION)
model = AutoModel.from_pretrained(MODEL, revision=MODEL_REVISION).eval()


@app.get("/health")
def health():
    return {"status": "ok", "modelVersion": MODEL_VERSION}


@app.post("/embed")
async def embed(file: UploadFile = File(...)):
    image = ImageOps.exif_transpose(Image.open(io.BytesIO(await file.read()))).convert("RGB")
    inputs = processor(images=image, return_tensors="pt")
    with torch.inference_mode():
        vector = model.get_image_features(**inputs)
        vector = torch.nn.functional.normalize(vector, dim=-1)[0].cpu().tolist()
    return {
        "modelVersion": MODEL_VERSION,
        "dimensions": len(vector),
        "vector": vector,
    }
