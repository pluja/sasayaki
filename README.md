# ささやき　（sasayaki）

A tiny dictation android app. Connect any OpenAI-Compatible backend, and type faster than you can imagine just by speaking.

Sasayaki is like [WisprFlow](https://wisprflow.ai) but free and can run on your own local models.

> IMPORTANT: This app was coded with AI. I am not an android developer, and I wouldn't be able to do this if it wasn't for AI. I try to enforce good practices, but any contributions or suggestions are very welcome. The app works pretty fine, is small, and gives me what I want. This README was written by me, zero AI.

## Features

- Floating dot that appears when the keyboard is open.
  - Click to start dictation; autodetects silence.
  - Hold for quick options.
- Lightweight; 2MB apk.
- Configure your main lanugages; helps improving ASR WER (Word Error Ratio).
- Word dictionary; sent as prompt to post processing llm and ASR; so the models know common words.
- History; keep all your dictation history. You can also turn this off.
- LLM Post Processing; configure an LLM to take your dictation and remove speech quirks and errors:
  - "So... We can meet at 6, wait no... actually 7, at the restaurant" -> "We can meet at 7 at the restaurant."
- And some more small features you will discover with usage.

## Self Hosting

### Requirements

- You will need an ASR backend that is compatible with OpenAI API (read below for more info).
- Optionally, you can set up an LLM to perform post-processing; that means it will remove the quirks from speach/dictation and transform it to a written-like message, removing the 'Uhhh..', 'Ah..', 'What was I gonna say?...'. 
- Android device.

### Self Hosting

This is an android app, so there's nothing to self host really. You can choose any backend provider, it just needs to provide an OpenAI-Compatible API.

For privacy and data ownership reasons, I recommend you self host your own backends; but there are reasonably private services such as [ppq.ai](https://ppq.ai/) or [nano-gpt.com](https://nano-gpt.com) that can serve this purpose if you trust them.

### Installing

Go to the [releases]() page, and get the latest APK. Install and you're good to go! You can also use Obtanium to have automated updates and so.

## Self Hosting your own backends

As mentioned before, I do recommend you self host your own backends. This will give you very good privacy and the joy of owning your data, and selfhosting.

Below you will find my personal recommendations, but the best you can do is just test and find whatever suits you better!

### The ASR (STT) service

There are many services you can self-host; from [whisper.cpp](https://github.com/ggml-org/whisper.cpp) to [faster-whisper](https://github.com/SYSTRAN/faster-whisper). I personally use and recommend [speaches](https://github.com/speaches-ai/speaches).

### Choosing a model

The best model you can use, from my personal testing in various European languages, is [`deepdml/faster-whisper-large-v3-turbo-ct2`](), it's very fast if you have a GPU and very accurate; even when speaking fast or whispering.

Other models you can use are:

- [whisper](https://huggingface.co/collections/openai/whisper-release) family, from the tiny ones to the medium. If you only intend to use it with english, I recommend you get the `en` models, which are more accurate and smaller. 
- [`Parakeet`](https://parakeettdt.com/) is also nice and has good results. 
- [`moonshine`](https://github.com/moonshine-ai/moonshine) is very small, multilingual and has very decent results; but I am not aware of any openai-compatible api implementation for it.
- [`Voxtral Mini`](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602)

## The LLM Post Processing

This is completely optional, but I found it has very good improvement on the resulting texts (low modification rate) with minimal overhead if you run small models (2B/4B) on consumer GPUs.

Personally, I have fine-tuned the 2B version of Qwen3.5 ([unsloth/Qwen3.5-2B](https://huggingface.co/unsloth/Qwen3.5-2B)) with the recipe you can find in the `fine-tuning` directory on this repo. You can generate synthetic dataset of data in your language and then perform the fine-tuning with [`unsloth`](https://unsloth.ai/), which is very fast and can be done with around 5GB of VRAM.

For the OpenAI-compatible backend, I recommend you use [`llama-swap`](https://github.com/mostlygeek/llama-swap) with llama.cpp backend; there are other nice options such as plan [`llama.cpp`](https://github.com/ggml-org/llama.cpp) or [`koboldcpp`](https://github.com/LostRuins/koboldcpp) or even [LlamaFiles](https://github.com/mozilla-ai/llamafile). The options are endless.

## License

GPLv3

## Why ささやき?

It means whisper in Japanese. I like how it sounds and looks. I am also learning Japanese, so I thought it's fun to use a Japanese word I learned for this tiny app.
