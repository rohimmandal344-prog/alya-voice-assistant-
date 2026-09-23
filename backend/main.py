from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.staticfiles import StaticFiles
import edge_tts
import asyncio
import os
import json
from groq import Groq
from dotenv import load_dotenv

load_dotenv()

app = FastAPI()

# Ensure audio directory exists
AUDIO_DIR = "static/audio"
os.makedirs(AUDIO_DIR, exist_ok=True)

# Mount static files to serve generated MP3s
app.mount("/static", StaticFiles(directory="static"), name="static")

# Groq Client
GROQ_API_KEY = os.getenv("GROQ_API_KEY")
client = Groq(api_key=GROQ_API_KEY) if GROQ_API_KEY else None

@app.websocket("/ws/chat")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    print("Client connected to Alya Bridge WebSocket")
    try:
        while True:
            # 1. Receive text from Android App
            user_text = await websocket.receive_text()
            print(f"Received from Alya: {user_text}")

            # 2. Generate AI Reply
            if client:
                try:
                    completion = client.chat.completions.create(
                        model="llama3-70b-8192",
                        messages=[
                            {"role": "system", "content": "You are Alya, a warm and helpful personal assistant. Speak naturally in Hindi/English mix (Hinglish)."},
                            {"role": "user", "content": user_text}
                        ],
                    )
                    ai_reply = completion.choices[0].message.content
                except Exception as e:
                    print(f"Groq Error: {e}")
                    ai_reply = f"Sorry, I encountered an error: {str(e)}"
            else:
                ai_reply = f"Aapne pucha: {user_text}. Groq API Key set nahi hai, isliye yeh default response hai."

            # 3. Generate Audio via Edge-TTS (Female Human-like Voice)
            # Using SwaraNeural for a warm, soft Hindi tone as requested
            filename = f"reply_{hash(ai_reply)}.mp3"
            file_path = os.path.join(AUDIO_DIR, filename)
            
            communicate = edge_tts.Communicate(ai_reply, "hi-IN-SwaraNeural")
            await communicate.save(file_path)

            # 4. Send JSON response back to Android
            # Note: 10.0.2.2 is Android Emulator default host address
            server_host = os.getenv("SERVER_HOST", "http://10.0.2.2:8000")
            audio_url = f"{server_host}/static/audio/{filename}"

            await websocket.send_json({
                "text": ai_reply,
                "audio_url": audio_url
            })
            print(f"Sent response and audio: {audio_url}")

    except WebSocketDisconnect:
        print("Client disconnected")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
