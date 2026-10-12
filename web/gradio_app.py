import os
os.environ["IS_GRADIO"] = "1"

import gradio as gr
import uvicorn
from backend.app.main import app as fastapi_app

# -----------------------------------------------------------------------------
# Gradio UI for quick interactive prescription analysis in the browser
# -----------------------------------------------------------------------------
with gr.Blocks(
    title="Gor3a API & Medical Prescription Lab",
    theme=gr.themes.Soft(primary_hue="teal")
) as demo:
    gr.Markdown(
        """
        # 💊 Gor3a (جرعة) - Egyptian Rx AI Backend & API
        ### Official Backend for Gor3a Mobile & Web Applications
        - **Powered by:** Egyptian Drug Authority (EDA) Database (25,000+ registered medicines) & Clinical Intelligence.
        - **Mobile API Endpoints:**
          - `POST /api/analyze` — Multipart image prescription entity extraction & EDA reconciliation.
          - `GET /api/drugs/search?query=...` — Instant Egyptian pharmacy price & candidate search.
          - `GET /api/drugs/{id}/image` — Regulatory packaging visual stream.
        """
    )
    with gr.Row():
        with gr.Column(scale=1):
            image_input = gr.Image(type="filepath", label="📷 Upload Prescription (صورة الروشتة)")
            analyze_btn = gr.Button("🔍 Analyze Prescription (تحليل الروشتة)", variant="primary")
        with gr.Column(scale=1):
            json_output = gr.JSON(label="Structured Extraction & Diagnosis (النتيجة الطبية المستخرجة)")

    async def run_gradio_analysis(img_path):
        if not img_path or not os.path.exists(img_path):
            return {"error": "Please upload a prescription image."}
        from backend.app.services.pipeline_v2 import pipeline_v2
        with open(img_path, "rb") as f:
            content = f.read()
        res = await pipeline_v2.process_prescription(
            image_bytes=content,
            filename=os.path.basename(img_path),
            mime_type="image/jpeg",
        )
        return res.model_dump()

    analyze_btn.click(
        fn=run_gradio_analysis,
        inputs=[image_input],
        outputs=[json_output]
    )

app = gr.mount_gradio_app(fastapi_app, demo, path="/")

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 7860))
    uvicorn.run(app, host="0.0.0.0", port=port)
