## Mali 빌드 추가 기능

- **Mali 그래픽 설정** (설정 → Mali 그래픽)
  - **PanVK (실험적):** Mesa의 오픈소스 Mali 드라이버([zenithblue-oss/panvk-kbase-android](https://github.com/zenithblue-oss/panvk-kbase-android))를 내려받아 쓸 수 있습니다. BC 텍스처를 CPU 대신 GPU에서 처리합니다. Galaxy Tab S10 Ultra에서 기기 자체 드라이버보다 빠르게 동작하는 것을 확인했습니다. 게임이 꺼지거나 기기가 재시작되면 기기 자체 드라이버로 되돌리세요.
  - **입력 지연 줄이기**, **가벼운 텍스처 변환:** 기기 자체 드라이버용 옵션입니다.
- **CurseForge 애드온 검색** (설정 → CurseForge 검색): 본인의 CurseForge API 키를 넣으면 CurseForge 애드온을 검색하고 설치할 수 있습니다. 키는 기기에 암호화해 저장하고 CurseForge에만 보냅니다. 검색에 실패하면 이유를 표시합니다.
- **Battle.net 로그인 페이지 방식**이 작동합니다. 로그인 도우미를 함께 빌드합니다.
- 한국어 런처, 한국 CDN 우회, 앱 안 업데이트는 이 저장소의 Mali 릴리스를 받습니다.
