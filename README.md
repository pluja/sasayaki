<p align="center"><a href="https://keepandroidopen.org/"><b>ANDROID WILL BECOME A LOCKED-DOWN PLATFORM</b></a></p>

# ささやき　（sasayaki）

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/{%22id%22:%22com.sasayaki%22,%22url%22:%22https://github.com/pluja/sasayaki%22,%22author%22:%22pluja%22,%22name%22:%22Sasayaki%22,%22additionalSettings%22:%22{\%22about\%22:\%22A%20tiny%20dictation%20app.%20Connect%20any%20OpenAI-compatible%20backend%20and%20type%20by%20speaking.\%22}%22}"><img src="/assets/badge_obtainium.png" alt="Get it on Obtainium" height="60"></a>

A tiny (2MB) Android dictation app. Point it at any OpenAI-compatible backend and type by speaking.

Sasayaki does the same job as [WisprFlow](https://wisprflow.ai), except it costs nothing and is more private, connecting to your own models.

> IMPORTANT: This app was coded with AI. I am not an android developer, and I wouldn't be able to do this if it wasn't for AI. I try to enforce good practices, but any contributions or suggestions are very welcome. The app works pretty fine, is small, and gives me what I want.

## Features

- A floating bubble appears when the keyboard opens. Tap it to dictate, and it stops when you go quiet.
  - While you speak it becomes a pill with a live waveform, a timer, pause, discard and stop.
  - Drag it anywhere; it docks to the nearest edge. Drop it on the target at the top to turn it off.
  - Long-press it to switch profile. Toggle it from a Quick Settings tile.
  - Recordings with no speech are dropped on the phone instead of being sent.
  - It refuses to record into password fields and apps it recognises as banking or payment apps, so nothing meant for them reaches a provider or your history.
  - Make the bubble as small as you like and its recording controls larger, or make it see-through or monochrome, from Appearance.
- **Keyboard mic key**: turn on Sasayaki voice input in Settings, and the mic key of keyboards like HeliBoard and FlorisBoard opens Sasayaki in the keyboard area, with your profiles one tap away. It types what you said and stays open for more; the keyboard key takes you back. Hide the keyboard or switch apps while you speak, and the recording carries on: open the keyboard again to see it, and the text lands in whichever field you are in when you stop, or on the clipboard if there is none. Leave the panel mid-dictation and the bubble carries the recording until you stop it, go quiet or open the panel again. No accessibility access needed for the panel itself, and the bubble can stay off.
- **Providers**: add any number of OpenAI-compatible servers, each with a default speech and cleanup model picked from its own model list. Keys are optional for self-hosted servers.
- **Profiles** bundle a language, a speech model, an optional cleanup model and a writing style. Switch the active one from the bubble, and Sasayaki remembers your pick for each app.
- **Post-processing**: a small model turns what you said into a clean written message.
  - "Uh... Yeah, let's meet at 8.. No sorry, at 9!" → "Let's meet at 9."
  - Per profile, set the punctuation and casing, how far the model may rewrite you, how much it should condense, and whether emoji are allowed. A sample sentence shows the effect as you change them.
  - Sasayaki passes the app you are dictating into as context, so mail comes out in a different tone from a chat. Dictations into AI assistants skip cleanup.
  - Dictated questions and commands are cleaned, never answered. Reasoning blocks, labels and runaway answers are caught, and the plain transcript is used instead.
  - Text added mid-sentence gets the spacing it needs.
- **Vocabulary**: names and jargon reach both the speech model and the cleanup model, so they come out spelled your way.
- Replacement rules fix the words your ASR keeps mangling. Plain text or regex, and each profile chooses which ones apply.
- Write your own post-processing prompts and switch them on per profile, alongside the built-in ones.
- History keeps past dictations and their audio, up to a limit you choose, and can be switched off. Turn on "Count words without history" to keep your totals while nothing else is saved.
- Nothing leaves the phone except requests to your providers: no analytics, no cloud backup, no device-to-device transfer.
- The bubble comes back on its own after a reboot.

<table>
  <tr>
    <td><img src="/assets/screenshot02.png" width="400"></td>
    <td><img src="/assets/screenshot04.png" width="400"></td>
  </tr>
  <tr>
    <td align="center"><i>Recording in a chat</i></td>
    <td align="center"><i>Done: the text is in the field</i></td>
  </tr>
</table>

<table>
  <tr>
    <td><img src="/assets/screenshot01.png" width="250"></td>
    <td><img src="/assets/screenshot03.png" width="250"></td>
  </tr>
  <tr>
    <td align="center"><i>Home</i></td>
    <td align="center"><i>A profile's writing style</i></td>
  </tr>
</table>

## Install

Grab the latest APK from the [releases](https://github.com/pluja/sasayaki/releases) page and install it. [Obtainium](https://github.com/ImranR98/Obtainium) can keep it updated for you:

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/{%22id%22:%22com.sasayaki%22,%22url%22:%22https://github.com/pluja/sasayaki%22,%22author%22:%22pluja%22,%22name%22:%22Sasayaki%22,%22additionalSettings%22:%22{\%22about\%22:\%22A%20tiny%20dictation%20app.%20Connect%20any%20OpenAI-compatible%20backend%20and%20type%20by%20speaking.\%22}%22}"><img src="/assets/badge_obtainium.png" alt="Get it on Obtainium" height="60"></a>

You need Android 9 or later and an ASR backend that speaks the OpenAI API. An LLM for post-processing is optional and worth it.

> **Updating from an older release:** releases are now signed with a new key. Android 9 and later accept the update as usual and keep your history and settings. Once you have it, an APK signed with the old key will no longer install over it.

## Backends

Nothing here needs hosting: the app is the whole product, and it will talk to any OpenAI-compatible API. Run your own if you care about privacy and owning your data. If you would rather not, [ppq.ai](https://ppq.ai/) and [nano-gpt.com](https://nano-gpt.com) are reasonable choices when you trust them. If you want a fully private backend, check out [tinfoil](https://tinfoil.sh).

### Speech to text

Plenty of options exist, from [whisper.cpp](https://github.com/ggml-org/whisper.cpp) to [faster-whisper](https://github.com/SYSTRAN/faster-whisper). I run [speaches](https://github.com/speaches-ai/speaches).

For the model itself, [`deepdml/faster-whisper-large-v3-turbo-ct2`](https://huggingface.co/deepdml/faster-whisper-large-v3-turbo-ct2) beat everything else I tried across several European languages. On a GPU it is quick, and it holds up when you talk fast or even whisper.

Other options:

- The [whisper](https://huggingface.co/collections/openai/whisper-release) family, from tiny up to medium. For English only, the `en` models are smaller and sharper.
- [`Parakeet`](https://parakeettdt.com/) gives good results.
- [`moonshine`](https://github.com/moonshine-ai/moonshine) is small and multilingual, though I know of no OpenAI-compatible API for it.
- [`Voxtral Mini`](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602)

Setting your main languages in the app lowers the word error rate.

### LLM post-processing

Optional. Any instruction-following chat model behind an OpenAI-compatible API works. Some starting points:

- **Hosted or a big GPU:** `gpt-oss-120b` is what I use day to day. `gpt-oss-20b` is fast on hosted inference such as Groq, but slow on modest local hardware.
- **Local on a consumer GPU:** the [Gemma 4](https://huggingface.co/google) family is the most common pick for local cleanup in other dictation apps.
- **Tiny and purpose-built:** [SpeakoFlow Mini](https://www.speakoflow.com/blog/speakoflow-mini-dictation-cleanup-model) is an Apache-2.0, English-only cleanup fine-tune of Qwen3.5-0.8B that runs on a CPU. [VoiceInk Refine](https://huggingface.co/beingpax/VoiceInk-Refine-V1) looks similar, but its license only allows use inside VoiceInk.

These are pointers from other projects, not benchmarks of mine; run the eval suite below against your own endpoint before settling on one.

To serve a local model, I use [`llama-swap`](https://github.com/mostlygeek/llama-swap) over a llama.cpp backend. Plain [`llama.cpp`](https://github.com/ggml-org/llama.cpp), [`koboldcpp`](https://github.com/LostRuins/koboldcpp) and [LlamaFiles](https://github.com/mozilla-ai/llamafile) all work too.

The same providers are useful if you don't want to host your own ([ppq.ai](https://ppq.ai/) and [nano-gpt.com](https://nano-gpt.com)). If you want private inference, check out [tinfoil](https://tinfoil.sh).

Pick a model that follows instructions. Some will answer a dictated question instead of transcribing it, so that "what is the capital of France" becomes "Paris" in your chat box. The eval suite below catches that.

## Development

Build the release APK in Docker, which avoids installing an Android SDK or a matching JDK:

```sh
make build      # writes sasayaki-release.apk
```

Run the unit tests and a debug build, which need no network and no credentials:

```sh
make verify
```

Before every push, [lefthook](https://github.com/evilmartians/lefthook) runs `make check`: formatting (ktlint through Spotless), Android lint and the unit tests. CI runs the same checks on every pull request. Install the hook once, and fix formatting with `make format`:

```sh
make hooks
make format
```

There is no emulator in the build container, so screens are checked with screenshot tests rendered on the JVM. They write PNGs to `app/build/screenshots/`:

```sh
make screenshots
```

The design rules the UI follows are in [docs/design-system.md](docs/design-system.md).

Two further suites measure the post-processing prompts against real models. They skip themselves unless `OPENAI_ENDPOINT` and `OPENAI_API_KEY` are set, so they never run by accident:

- `PostProcessingBenchmark` sends dictations in English, Spanish, French, Catalan and Italian, then checks whether self-corrections were applied, whether the text stayed in its original language, and how long each model took.
- `StyleControlDifferentialTest` verifies that moving a style control changes the output, which is how I found two settings that did nothing at all.

Both write a report under `app/build/reports/benchmark/`.

### Release signing

Releases are signed with a private key that never enters the repository. Versions up to and including the last debug-signed release used the public debug key, so `signing/lineage` holds a one-time proof, signed by that key, that hands updates over to the new one. Phones on Android 9 and later accept the new releases as normal updates. Once a phone has installed one, an APK signed only with the debug key is refused.

Set it up once:

1. `make release-key` creates `signing/release.jks` and `signing/lineage`, asking for a password.
2. Back up `signing/release.jks` and its password somewhere safe. Losing them means every user has to reinstall.
3. Commit `signing/lineage`. It holds only certificates and a signature, and every release needs it.
4. Add two repository secrets for the release workflow: `RELEASE_KEYSTORE_BASE64` (the output of `base64 -w0 signing/release.jks`) and `RELEASE_KEYSTORE_PASSWORD`.

`make build` then signs locally, reading the password from `SASAYAKI_KEYSTORE_PASSWORD` or asking for it.

## License

GPLv3

## Why ささやき?

It means whisper in Japanese. I like how it sounds and looks. I am also learning Japanese, so I thought it's fun to use a Japanese word I learned for this tiny app.
