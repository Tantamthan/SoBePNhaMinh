plugins {
    alias(libs.plugins.android.application)
    // Firebase: cần file app/google-services.json (tải từ Firebase Console)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.sobep"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sobep"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Khoá ký DÙNG CHUNG cho bản debug (thay cho ~/.android/debug.keystore riêng của từng máy).
    // Đăng nhập Google kiểm tra SHA-1 của khoá ký APK: dùng chung 1 khoá nằm trong project thì
    // máy nào build cũng ra cùng SHA-1 (đã đăng ký trên Firebase Console) → nút Google chạy trên mọi máy.
    // Chỉ dùng cho debug / đồ án nên để mật khẩu ở đây; app phát hành thật phải dùng khoá release giữ bí mật.
    signingConfigs {
        getByName("debug") {
            storeFile = file("sobep-shared.keystore")
            storePassword = "android"
            keyAlias = "sobep"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        // Sinh class Binding cho mỗi layout XML (vd. ActivityMainBinding)
        viewBinding = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Room: lưu dữ liệu offline bằng SQLite. Dự án viết Java nên dùng annotationProcessor
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Lifecycle: ViewModel (giữ dữ liệu khi xoay màn hình) và LiveData (tự cập nhật giao diện)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // ML Kit nhận dạng chữ (bản kèm sẵn mô hình trong APK): đọc chữ trong ảnh ngay trên máy, không cần mạng
    implementation(libs.mlkit.text.recognition)

    // Firebase Auth (CN13): đăng ký / đăng nhập / đăng xuất bằng email + mật khẩu.
    // Đăng nhập là tuỳ chọn: không đăng nhập app vẫn chạy offline với Room
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // Cloud Firestore (CN14): sao lưu / đồng bộ món ăn (chỉ chữ) lên mây theo uid người dùng
    implementation(libs.firebase.firestore)

    // Đăng nhập bằng Google (CN13 mở rộng): Credential Manager hiện bảng chọn tài khoản Google
    // → lấy ID token → đưa cho Firebase Auth. credentials-play-services-auth cần cho Android 13 trở xuống
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}