import io, os
from fastapi import FastAPI, UploadFile, File
from PIL import Image, ImageOps
import torch
from transformers import AutoModel, AutoProcessor

MODEL = os.getenv("SIGLIP_MODEL", "google/siglip-base-patch16-224")
app = FastAPI(title="义采通 SigLIP Service")
processor = AutoProcessor.from_pretrained(MODEL)
model = AutoModel.from_pretrained(MODEL).eval()


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL}


@app.post("/embed")
async def embed(file: UploadFile = File(...)):
    image = ImageOps.exif_transpose(Image.open(io.BytesIO(await file.read()))).convert("RGB")
    inputs = processor(images=image, return_tensors="pt")
    with torch.inference_mode():
        vector = model.get_image_features(**inputs)
        vector = torch.nn.functional.normalize(vector, dim=-1)[0].cpu().tolist()
    return {"modelVersion": MODEL, "dimensions": len(vector), "vector": vector}
