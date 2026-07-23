# YTMate

App Android para baixar vídeos e músicas do YouTube com seletor de qualidade, construído com **Jetpack Compose** e **Material You M3** (Design 3).

## Recursos

- 🎬 Baixa vídeos em diferentes qualidades (144p até a máxima disponível)
- 🎵 Baixa apenas o áudio em diferentes bitrates (m4a/opus)
- 🔗 Integração com o **Compartilhar** do YouTube: ao compartilhar um vídeo no app do YouTube, o YTMate aparece na lista e abre automaticamente com os detalhes do vídeo
- 🎨 Material You M3 com **cores dinâmicas** (Android 12+) e suporte a tema claro/escuro
- 📊 Tela de progresso em tempo real, com notificação foreground e botão de cancelar
- 📁 Salva vídeos em `Movies/YTMate` e áudios em `Music/YTMate`

## Como usar

1. Abra o app do YouTube no celular
2. Toque em **Compartilhar** abaixo do vídeo
3. Selecione **YTMate** na lista de apps
4. O YTMate abre automaticamente com os detalhes do vídeo
5. Escolha entre **Vídeo** ou **Áudio** e selecione a qualidade desejada
6. Toque em **Baixar** — o download começa e aparece na lista de downloads

Também é possível colar manualmente a URL do YouTube no campo de texto do app.

## Tecnologias

- **Kotlin 2.0** + **Jetpack Compose** (BOM 2024.12)
- **Material 3** com esquema de cores dinâmico (`dynamicLightColorScheme`/`dynamicDarkColorScheme`)
- **NewPipe Extractor** para extração de streams do YouTube
- **OkHttp** para downloads streaming
- **Coil** para carregamento de thumbnails
- **Foreground Service** para downloads robustos com notificação
- Min SDK 24, Target SDK 35

## Build

```bash
./gradlew assembleDebug
```

O APK de debug será gerado em `app/build/outputs/apk/debug/`.

## GitHub Actions

O workflow `.github/workflows/build.yml` compila o APK de debug e release a cada push para `main`. Os artefatos ficam disponíveis para download na aba **Actions** do GitHub.

## Aviso legal

Este projeto é apenas para fins educacionais e uso pessoal. Baixar conteúdo do YouTube pode violar os Termos de Serviço da plataforma. Use por sua conta e risco e apenas para conteúdo que você tem o direito de baixar.
