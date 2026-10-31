"# baitapSpringAI_Nhom9" 

Cách cài:
- Cách 1: git clone
- Cách 2: download zip, rồi vào IDE import project, chỉnh application.properties, rồi chạy file EshopApplication.java dạng Java Application

Project sẽ chạy theo port trong file application.properties (localhost:xxxx)

Cách chạy GEMINI
1. Tạo biến môi trường trong máy (không nên, dễ quên xóa)
### tạo GEMINI_API_KEY ở https://aistudio.google.com/api-keys?utm_source=chatgpt.com
### rồi chạy trong cmd: ```setx GEMINI_API_KEY "GEMINI_API_KEY"``` (lệnh tạo biến môi trường trong máy)
### và kiểm tra ```echo %GEMINI_API_KEY%```
GEMINI_API_KEY là cái đã tạo 

2. Viết trực tiếp vào (chỉ chạy trong máy, ko push lên)
```
spring.ai.google.genai.api-key=${GEMINI_API_KEY}
```
thay phần ${GEMINI_API_KEY} bằng API mình có

Ngoài ra chọn model và cấu hình dưới đây
```
spring.ai.google.genai.chat.options.model=gemini-3.5-flash-lite
spring.ai.google.genai.chat.options.temperature=0.3
```
