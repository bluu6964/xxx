# GPU (OpenGL ES / Vulkan) Rendering Engine Architecture Plan
## মোশন স্টুডিও (Motion Studio) - জিপিইউ-ভিত্তিক রেন্ডারিং এবং এক্সপোর্ট পাইপলাইন

অ্যান্ড্রয়েডে একটি রিয়েল-টাইম ভিডিও এডিটরের প্রিভিউ এবং এক্সপোর্ট পারফরম্যান্সের জন্য জিপিইউ (GPU) ব্যবহার করা অত্যন্ত জরুরি। বর্তমানে মোশন স্টুডিওতে সিইউ (CPU) ভিত্তিক কম্পোজিটিং ও YUV কনভার্সন করা হচ্ছে। নিচে জিপিইউ (OpenGL ES / Vulkan) আর্কিটেকচার এবং এর বাস্তবায়ন পরিকল্পনা (Detailed Architectural Plan) তুলে ধরা হলো।

---

## ১. প্রজেক্টের জিপিইউ রেন্ডারিং এর প্রধান লক্ষ্য (Objectives)
1. **Zero CPU-to-GPU Copy (জিরো-কপি পাইপলাইন):** ডিকোড করা ভিডিও ফ্রেম বা ইমেজ সরাসরি জিপিইউ মেমরিতে থাকবে এবং এনকোডারে পাঠানোর সময় কোনো আরজিবি-টু-YUV কনভার্সন সিপিইউতে হবে না।
2. **60 FPS Real-time Preview:** ৪কে (4K) বা ১০৮০পি (1080p) রেজোলিউশনে অনেকগুলো লেয়ার এবং ইফেক্টস থাকা সত্ত্বেও রিয়েল-টাইম প্রিভিউ নিশ্চিত করা।
3. **Hardware-Accelerated Encoding:** `MediaCodec` এর সাথে **Input Surface** যুক্ত করে সরাসরি জিপিইউ থ্রেড থেকে এনকোডারের মেমরিতে ড্র করা।
4. **Extensible Shader-based Effects:** সমস্ত ভিজ্যুয়াল ইফেক্টস (যেমন: Blur, Glitch, Chromatic Aberration) এবং ব্লেন্ড মোড জিপিইউ-এর ফ্র্যাগমেন্ট শেডারে (Fragment Shader / GLSL) রান করানো।

---

## ২. OpenGL ES নাকি Vulkan? (The Technology Choice)

অ্যান্ড্রয়েডের জন্য আমাদের কাছে দুটি প্রধান গ্রাফিক্স এপিআই রয়েছে:
- **OpenGL ES 3.0+**
- **Vulkan**

### তুলনামূলক বিশ্লেষণ (Comparison):

| বৈশিষ্ট্য | OpenGL ES 3.0 | Vulkan |
| :--- | :--- | :--- |
| **সাপোর্ট ও ডিভাইসের সামঞ্জস্য** | অ্যান্ড্রয়েড ৫.০ (API 21)+ থেকে শতভাগ ডিভাইসে নির্ভরযোগ্য সাপোর্ট। | অ্যান্ড্রয়েড ৭.০+ (API 24+) থেকে সাপোর্ট, তবে ওল্ডার ডিভাইসে ড্রাইভার বাগ থাকতে পারে। |
| **জটিলতা ও ডেভেলপমেন্ট সাইকেল** | তুলনামূলক সহজ ও দ্রুত বাস্তবায়নযোগ্য। স্টেট মেশিন ভিত্তিক। | অত্যন্ত জটিল, মেমরি অ্যালোকেশন ও সিঙ্ক ম্যানুয়ালি করতে হয়। |
| **MediaCodec / SurfaceTexture সংযোগ** | `OES_EGL_image_external` এর মাধ্যমে সরাসরি অ্যান্ড্রয়েড ভিডিও ফ্রেম টেক্সচার স্যাম্পলিং করা যায়। | Vulkan-এ `SurfaceTexture` এর সাথে ইন্টিগ্রেশন জটিল এবং ড্রাইভারভেদে আলাদা হতে পারে। |
| **পারফরম্যান্স** | মোশন স্টুডিওর মতো লেয়ার-ভিত্তিক ২ডি ভিডিও এডিটরের জন্য পর্যাপ্ত পারফরম্যান্স দেবে। | মাল্টি-থ্রেডেড রেন্ডারিং ও অত্যন্ত জটিল ৩ডি সীনের জন্য সেরা। |

### **সিদ্ধান্ত (Our Recommendation):**
**OpenGL ES 3.0** ব্যবহার করা মোশন স্টুডিওর জন্য সবচেয়ে বুদ্ধিমানের কাজ হবে। এটি অ্যান্ড্রয়েডের `SurfaceTexture` এবং `MediaCodec.createInputSurface()` এর সাথে সরাসরি সংযুক্ত এবং এর ডেভেলপমেন্ট জটিলতা অনেক কম। পরবর্তীতে খুব বেশি রেন্ডার ওভারহেড থাকলে এর কিছু পার্টস Vulkan-এ পোর্ট করা যেতে পারে।

---

## ৩. জিপিইউ-ভিত্তিক মিডিয়া পাইপলাইন (Proposed GPU Pipeline)

বর্তমান সিপিইউ পাইপলাইনের সাথে জিপিইউ পাইপলাইনের মূল পার্থক্য নিচে দেখানো হলো:

### ওল্ড সিপিইউ পাইপলাইন (ধীরগতির):
`Video Frame/Bitmap` -> `CPU Compositing (Canvas/C++)` -> `CPU RGBA Buffer` -> `YUV Conversion (CPU/C++)` -> `Byte Buffer` -> `MediaCodec Input Buffer` -> `Encoder` (প্রচুর মেমরি কপি ও সিপিইউ সাইকেল নষ্ট)।

### নতুন জিপিইউ পাইপলাইন (বিদ্যুৎগতি):
```
┌─────────────────┐      ┌─────────────────┐      ┌────────────────┐
│   Video Layer   │      │   Image Layer   │      │  Vector/Text   │
│ (SurfaceTexture│      │ (GL Texture 2D) │      │ (Rasterized/GL)│
└────────┬────────┘      └────────┬────────┘      └────────┬───────┘
         │                        │                        │
         ▼                        ▼                        ▼
┌──────────────────────────────────────────────────────────────────┐
│              OpenGL ES Renderer (EGL Context)                    │
│   - Multi-Texture Sampling                                       │
│   - Vertex Shader (Affine Transforms: Offset, Scale, Rotate)     │
│   - Fragment Shader (Opacity, Blend Modes, Effects/Shaders)      │
└────────────────────────────────┬─────────────────────────────────┘
                                 │ (Render directly to)
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│      EGL Window Surface (Created from MediaCodec Input Surface)  │
│    * Hardware automatisch RGBA to YUV কনভার্সন সম্পন্ন করে *     │
└────────────────────────────────┬─────────────────────────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │  MediaCodec Hardware    │ -> [MP4 Output]
                    └─────────────────────────┘
```

---

## ৪. আর্কিটেকচারাল কম্পোনেন্টস (Architectural Components)

জিপিইউ পাইপলাইন বাস্তবায়নের জন্য অ্যান্ড্রয়েড এবং নেটিভ (C++) স্তরে নিম্নোক্ত ক্লাস ও অবজেক্টগুলোর প্রয়োজন হবে:

### ক) কোটলিন স্তর (Kotlin/JVM Layer)
1. **`EGLCore`:** EGL Context, Display এবং Configuration হ্যান্ডেল করবে। এটি প্রিভিউ (EGL Surface) এবং এনকোডার (Input Surface) উভয় ক্ষেত্রে ব্যবহৃত হবে।
2. **`InputSurface` / `CodecInputSurface`:** `MediaCodec.createInputSurface()` থেকে প্রাপ্ত সারফেসটিকে রেন্ডারিং-এর জন্য উইন্ডো সারফেস হিসেবে র্যাপ করবে।
3. **`GPUVideoRenderer`:** বর্তমান `VideoRenderer`-কে জিপিইউ সংস্করণে রূপান্তর করবে। ফ্রেম রেন্ডারিং লুপ পরিচালনা করবে এবং প্রতি ফ্রেমের জন্য কো-অর্ডিনেট সেটআপ করবে।
4. **`STTextureRender`:** `SurfaceTexture` থেকে আসা ভিডিও ফ্রেম টেক্সচার স্যাম্পল করার ইউটিলিটি।

### খ) সি++ স্তর (Native GLES C++ Engine)
1. **`GLESCompositor`:** লেয়ার রেন্ডারিং অ্যালগরিদম। লেয়ারগুলোর ডেটা JNI দিয়ে পাঠালে এটি ড্র-কলগুলো সম্পাদন করবে।
2. **`Shader`:** GLSL কম্পাইলেশন ও লিঙ্কিং স্ক্রিপ্ট।
3. **`TextureCache`:** ডিকোড হওয়া ভিডিও বা ইমেজ ফ্রেমগুলোকে জিপিইউ মেমরিতে রিউজেবল টেক্সচার হিসেবে ধরে রাখবে।

---

## ৫. মূল শেডার কোড নকশা (GLSL Shader Blueprint)

জিপিইউ-তে হাই-পারফরম্যান্স কম্পোজিটিং-এর জন্য আমরা **Vertex Shader** এবং **Fragment Shader** ব্যবহার করব।

### Vertex Shader (`compositor_vertex.glsl`)
এটি প্রতি লেয়ারের অবস্থান (Translate), ঘূর্ণন (Rotate) এবং স্কেল (Scale) হ্যান্ডেল করবে প্যারালাল জিপিইউ কোরগুলোতে।

```glsl
#version 300 es
layout(location = 0) in vec4 aPosition;     // Vertex Coordinates [-1, 1]
layout(location = 1) in vec2 aTexCoord;     // Texture Coordinates [0, 1]

out vec2 vTexCoord;
uniform mat4 uMVPMatrix; // Model-View-Projection Matrix (Holds translation, rotation, scale)

void main() {
    gl_Position = uMVPMatrix * aPosition;
    vTexCoord = aTexCoord;
}
```

### Fragment Shader (`compositor_fragment.glsl`)
এটি লেয়ারের ট্রান্সপারেন্সি, ব্লেন্ড মোড এবং ভিজ্যুয়াল ইফেক্টস প্রসেস করবে।

```glsl
#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture; // The layer bitmap/frame texture
uniform float uOpacity;     // Dynamic Opacity [0.0 - 1.0]

// Optional effects flags
uniform int uEffectType;    // 0 = None, 1 = Blur, 2 = Saturation, etc.
uniform float uEffectStrength;

void main() {
    vec4 texColor = texture(uTexture, vTexCoord);
    
    // Apply Opacity (Premultiplied-alpha conversion on GPU)
    texColor.rgb *= uOpacity;
    texColor.a *= uOpacity;
    
    // Example: Basic Grayscale Effect on GPU
    if (uEffectType == 2) {
        float gray = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));
        texColor.rgb = mix(texColor.rgb, vec3(gray) * texColor.a, uEffectStrength);
    }
    
    fragColor = texColor;
}
```

---

## ৬. কোটলাইন ইন্টারফেস এবং জেএনআই ব্রিজের রূপরেখা (Draft/Blueprint API)

বাস্তবায়ন শুরু করতে আমরা যে এপিআই ডিজাইনটি ব্যবহার করব:

### ১. কোটলিন সাইড জিপিইউ রেন্ডারার (`com.example.render.GPURenderer`)
```kotlin
package com.example.render

import android.view.Surface
import android.graphics.Bitmap

object GPURenderer {
    // Initialize OpenGL context and shaders
    external fun nativeInit()
    
    // Release all GL assets
    external fun nativeRelease()
    
    // Bind the target render surface (Preview surface or MediaCodec Encoder surface)
    external fun nativeBindSurface(surface: Surface, width: Int, height: Int)
    
    // Prepare a texture from an Android Bitmap
    external fun nativeUploadTexture(bitmap: Bitmap): Int
    
    // Draw all layers back-to-front onto the bound surface
    external fun nativeRenderFrame(
        layerTextureIds: IntArray,
        opacities: FloatArray,
        mvpMatrices: FloatArray, // 16 floats per layer matrix
        effectTypes: IntArray,
        effectStrengths: FloatArray
    )
}
```

---

## ৭. ৪-ধাপের বাস্তবায়ন রোডম্যাপ (Step-by-Step Implementation Roadmap)

GPU ইঞ্জিনে ট্রানজিশনটি নিরাপদ এবং ধাপে ধাপে করতে হবে যাতে অ্যাপের বর্তমান ফিচারগুলো ভেঙে না যায়:

```
  Phase 1: EGL Context & Setup ─────► Phase 2: Shader-based Transforms
             │                                        │
             ▼                                        ▼
  Phase 3: MediaCodec Integration ──► Phase 4: Shader Effects (GLSL)
```

### **ধাপ ১: EGL Context এবং কোটলিন স্কেলেটন তৈরি (Week 1)**
- `EGLCore` এবং কাস্টম `GLSurfaceView` বা `TextureView` প্রিভিউ-এর জন্য রেডি করা।
- কোটলাইনে `GPURenderer` ডিজাইন ও এনডিকে (CMake) এর সাথে OpenGL ES লিঙ্ক করা।

### **ধাপ ২: শেডারভিত্তিক রেন্ডারিং ও কম্পোজিটিং (Week 2)**
- জিপিইউ-তে ইমেজ এবং শেপ লেয়ার আপলোড করা।
- Vertex Shader-এ Affine Matrix গুণের মাধ্যমে মুভমেন্ট ও রোটেশন ইমপ্লিমেন্ট করা।
- Fragment Shader-এ Porter-Duff ব্রেন্ডিং লজিক ইমপ্লিমেন্ট করা।

### **ধাপ ৩: MediaCodec Input Surface পাইপলাইন প্রতিস্থাপন (Week 3)**
- `VideoRenderer.kt` এ এনকোডারের Input Surface তৈরি করা।
- ফাস্ট-এক্সপোর্ট টেস্টিং: আরজিবিএ থেকে YUV-তে কনভার্সনটি সরাসরি হার্ডওয়্যারের কাছে হস্তান্তর করা।
- ডিকোড করা ভিডিও ফ্রেম টেক্সচার স্যাম্পল করার জন্য `SurfaceTexture` কানেক্ট করা।

### **ধাপ ৪: ইফেক্টস এবং ফিল্টারগুলোকে GLSL শেডারে কনভার্ট করা (Week 4)**
- `EffectsModifier.kt` থেকে ব্লার, কালারাইজ, স্যাচুরেশন, ব্রাইটনেস ইত্যাদি জটিল ফিল্টারগুলোকে ডেডিকেটেড GLSL শেডারে পোর্ট করা।

---

## ৮. জিপিইউ রেন্ডারিং এর সম্ভাব্য পারফরম্যান্স আউটপুট (Expected Impact)

- **রেন্ডারিং স্পিড:** ২-৩ গুণ বেশি দ্রুত এক্সপোর্ট স্পিড (পিক্সেল ডেটা সিপিইউ-তে হ্যান্ডেল করার চেয়ে এটি শতভাগ অপ্টিমাইজড)।
- **মেমরি ব্যবহার:** জিরো মেমরি লিক এবং মেমরি বাফার অ্যালোকেশন জিরো হয়ে যাবে।
- **ডিভাইসের তাপমাত্রা হ্রাস:** সিপিইউ-এর উপর প্রেসার কমে যাওয়ার কারণে রেন্ডারিং-এর সময় ফোন গরম হবে না এবং ব্যাটারি কম খরচ হবে।

---

*আমি এই সম্পূর্ণ প্ল্যানটি `/home/user/xxx/GPU_ARCHITECTURE_PLAN.md` ফাইলে লিখে সংরক্ষণ করে রেখেছি যাতে ফিউচার ডেভেলপমেন্টে এটি গাইডবুক হিসেবে ব্যবহার করা যায়।*
