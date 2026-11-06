package com.sobep.ui.addedit;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sobep.R;
import com.sobep.data.local.entity.Ingredient;
import com.sobep.data.local.entity.Recipe;
import com.sobep.data.local.entity.Step;
import com.sobep.databinding.ActivityAddEditRecipeBinding;
import com.sobep.ui.BaseActivity;
import com.sobep.ui.scan.ScanRecipeActivity;
import com.sobep.util.ImageStorage;
import com.sobep.util.IntentKeys;
import com.sobep.util.RecipeTextParser;

import java.io.File;
import java.io.IOException;

/**
 * Màn 5 · Thêm / Sửa món (CN03, CN04). Một màn dùng cho 2 chế độ:
 * - Intent KHÔNG có mã món → Thêm mới.
 * - Intent CÓ mã món → Sửa, điền sẵn thông tin món đọc từ CSDL.
 * Bấm Lưu → AddEditRecipeViewModel ghi vào Room → trả RESULT_OK về màn đã mở nó.
 * Thẻ "Quét chữ từ ảnh" → ScanRecipe → RecipeTextParser tách chữ vào từng ô.
 * Khung ảnh món: "Chọn ảnh" (bộ chọn ảnh hệ thống, không cần quyền) hoặc "Chụp ảnh" (xin quyền CAMERA lúc chạy
 * → app Camera). Đã có ảnh → bấm vào ảnh để chọn ảnh khác, chụp ảnh khác hoặc xoá ảnh.
 */
public class AddEditRecipeActivity extends BaseActivity {

    // Khẩu phần hợp lệ, khớp với giới hạn nút +/- ở màn Chi tiết món
    private static final int MIN_SERVINGS = 1;
    private static final int MAX_SERVINGS = 20;
    // Lưu Uri ảnh đang chờ app Camera chụp, phòng khi hệ thống tạo lại màn này lúc đang ở app Camera
    private static final String STATE_PENDING_PHOTO_URI = "pending_photo_uri";

    private ActivityAddEditRecipeBinding binding;
    private AddEditRecipeViewModel viewModel;
    private IngredientEditAdapter ingredientAdapter;
    private StepEditAdapter stepAdapter;

    // File app Camera sẽ ghi ảnh món vào (chỉ dùng khi chụp thành công)
    private Uri pendingPhotoUri;

    // Mở màn Quét công thức và nhận lại đoạn chữ quét được
    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String scannedText = result.getData().getStringExtra(IntentKeys.EXTRA_SCANNED_TEXT);
                    fillFromScannedText(scannedText);
                }
            });

    // Ảnh món · 1. Xin quyền CAMERA lúc chạy → hệ thống hiện hộp thoại "Cho phép / Không cho phép"
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    launchCamera();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    // Mới từ chối 1 lần: lần sau vẫn hỏi lại được
                    showToast(R.string.permission_camera_denied);
                } else {
                    // Từ chối hẳn ("Không hỏi lại"): hệ thống không hiện hộp thoại nữa → chỉ đường vào Cài đặt
                    showPermissionBlockedDialog();
                }
            });

    // Ảnh món · 2. Mở app Camera chụp, ảnh ghi vào pendingPhotoUri. Kết quả: true = đã chụp
    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            saved -> {
                if (Boolean.TRUE.equals(saved) && pendingPhotoUri != null) {
                    viewModel.setPhoto(pendingPhotoUri, true);
                }
                pendingPhotoUri = null;
            });

    // Ảnh món · 3. Bộ chọn ảnh của hệ thống (Photo Picker), không cần quyền đọc bộ nhớ. Huỷ → null
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) {
                    viewModel.setPhoto(uri, false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddEditRecipeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        long recipeId = getIntent().getLongExtra(IntentKeys.EXTRA_RECIPE_ID, IntentKeys.NO_RECIPE_ID);
        binding.textScreenTitle.setText(recipeId == IntentKeys.NO_RECIPE_ID
                ? R.string.add_recipe_title : R.string.edit_recipe_title);

        viewModel = new ViewModelProvider(this).get(AddEditRecipeViewModel.class);
        // Thêm → món trống; Sửa → ViewModel đọc món từ CSDL (gọi lại khi xoay màn hình thì bỏ qua)
        viewModel.start(recipeId);

        if (savedInstanceState != null) {
            String pending = savedInstanceState.getString(STATE_PENDING_PHOTO_URI);
            pendingPhotoUri = pending != null ? Uri.parse(pending) : null;
        }

        setupLists();
        setupActions();
        observeViewModel();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingPhotoUri != null) {
            outState.putString(STATE_PENDING_PHOTO_URI, pendingPhotoUri.toString());
        }
    }

    private void setupLists() {
        // LinearLayoutManager đã khai báo trong activity_add_edit_recipe.xml
        ingredientAdapter = new IngredientEditAdapter(position -> viewModel.removeIngredient(position));
        binding.recyclerIngredients.setAdapter(ingredientAdapter);
        stepAdapter = new StepEditAdapter(position -> viewModel.removeStep(position));
        binding.recyclerSteps.setAdapter(stepAdapter);
    }

    private void observeViewModel() {
        viewModel.getRecipe().observe(this, recipe -> {
            // Chỉ điền form lần đầu. Xoay màn hình: các ô tự giữ chữ đang gõ, không điền đè
            if (recipe != null && !viewModel.isFormFilled()) {
                fillForm(recipe);
                viewModel.markFormFilled();
            }
        });
        viewModel.getIngredients().observe(this, ingredientAdapter::setItems);
        viewModel.getSteps().observe(this, stepAdapter::setItems);
        viewModel.getPhotoPath().observe(this, this::showPhoto);
        viewModel.getPhotoError().observe(this, error -> {
            if (Boolean.TRUE.equals(error)) {
                showToast(R.string.msg_photo_error);
                viewModel.clearPhotoError();
            }
        });

        viewModel.getNotFound().observe(this, notFound -> {
            if (Boolean.TRUE.equals(notFound)) {
                showToast(R.string.msg_recipe_not_found);
                finish();
            }
        });
        // Lưu xong → báo màn trước (Main hoặc Chi tiết) rồi đóng
        viewModel.getSavedRecipeId().observe(this, savedId -> {
            if (savedId != null) {
                setResult(Activity.RESULT_OK);
                finish();
            }
        });
    }

    /** Điền thông tin món vào các ô nhập (chế độ Sửa) hoặc giá trị mặc định (chế độ Thêm). */
    private void fillForm(Recipe recipe) {
        binding.editName.setText(recipe.name);
        binding.spinnerCategory.setSelection(getCategoryIndex(recipe.category));
        binding.editCookTime.setText(recipe.cookTimeMinutes > 0 ? String.valueOf(recipe.cookTimeMinutes) : "");
        binding.editServings.setText(String.valueOf(recipe.servings));
        binding.editVideoUrl.setText(recipe.videoUrl);
        binding.editNote.setText(recipe.note);
    }

    /** Mã danh mục (vd "soup") → vị trí trong Spinner (Spinner dùng string-array recipe_categories cùng thứ tự). */
    private int getCategoryIndex(String category) {
        for (int i = 0; i < Recipe.CATEGORIES.length; i++) {
            if (Recipe.CATEGORIES[i].equals(category)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Điền chữ quét được vào form. ViewModel tách chữ (RecipeTextParser) và thêm nguyên liệu, các bước vào cuối danh sách.
     * Tên món, thời gian, khẩu phần, ghi chú: CHỈ điền khi ô đang trống → không ghi đè chữ người dùng đã nhập.
     * Khẩu phần ở chế độ Thêm luôn có sẵn số mặc định (2) nên coi số mặc định đó như ô trống.
     */
    private void fillFromScannedText(String scannedText) {
        if (TextUtils.isEmpty(scannedText)) {
            return;
        }
        RecipeTextParser.Result result = viewModel.applyScannedText(scannedText);

        if (!result.name.isEmpty() && textOf(binding.editName).isEmpty()) {
            binding.editName.setText(result.name);
            binding.editName.setError(null);
        }
        if (result.cookTimeMinutes > 0 && textOf(binding.editCookTime).isEmpty()) {
            binding.editCookTime.setText(String.valueOf(result.cookTimeMinutes));
        }
        String currentServings = textOf(binding.editServings);
        boolean servingsUntouched = currentServings.isEmpty()
                || (!viewModel.isEditMode() && currentServings.equals(String.valueOf(new Recipe().servings)));
        if (result.servings >= MIN_SERVINGS && result.servings <= MAX_SERVINGS && servingsUntouched) {
            binding.editServings.setText(String.valueOf(result.servings));
        }
        if (!result.otherLines.isEmpty() && textOf(binding.editNote).isEmpty()) {
            binding.editNote.setText(TextUtils.join("\n", result.otherLines));
        }
        showToast(getString(R.string.msg_scan_filled, result.ingredients.size(), result.steps.size()));
    }

    private void setupActions() {
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonSaveTop.setOnClickListener(v -> saveRecipe());
        binding.buttonSave.setOnClickListener(v -> saveRecipe());

        binding.cardScan.setOnClickListener(v ->
                scanLauncher.launch(new Intent(this, ScanRecipeActivity.class)));

        binding.buttonPickPhoto.setOnClickListener(v -> pickPhoto());
        binding.buttonTakePhoto.setOnClickListener(v -> takePhotoWithPermission());
        // Đã có ảnh: bấm vào ảnh → chọn ảnh khác / chụp ảnh khác / xoá ảnh
        binding.imagePhoto.setOnClickListener(v -> showPhotoOptions());

        // Thêm một dòng trống vào bản nháp rồi đặt con trỏ vào ô đầu của dòng mới
        binding.buttonAddIngredient.setOnClickListener(v -> {
            viewModel.addIngredient(new Ingredient());
            focusLastRow(binding.recyclerIngredients);
        });
        binding.buttonAddStep.setOnClickListener(v -> {
            viewModel.addStep(new Step());
            focusLastRow(binding.recyclerSteps);
        });
    }

    // ---------- Ảnh món ----------

    /** Có ảnh → hiện ảnh, ẩn 2 nút; không có ảnh (hoặc file đã mất) → hiện lại 2 nút Chọn ảnh / Chụp ảnh. */
    private void showPhoto(String path) {
        boolean hasPhoto = path != null && new File(path).exists();
        // Gán null trước: setImageURI bỏ qua nếu Uri trùng Uri cũ
        binding.imagePhoto.setImageURI(null);
        if (hasPhoto) {
            binding.imagePhoto.setImageURI(Uri.fromFile(new File(path)));
        }
        binding.imagePhoto.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        binding.layoutPhotoActions.setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
    }

    private void showPhotoOptions() {
        String[] options = {
                getString(R.string.action_pick_other_photo),
                getString(R.string.action_take_other_photo),
                getString(R.string.action_remove_photo)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.photo_options_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickPhoto();
                    } else if (which == 1) {
                        takePhotoWithPermission();
                    } else {
                        viewModel.removePhoto();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Mở bộ chọn ảnh của hệ thống, chỉ hiện ảnh (không hiện video). */
    private void pickPhoto() {
        pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    /** Đã có quyền CAMERA → chụp luôn; chưa có → giải thích (nếu cần) rồi xin quyền. */
    private void takePhotoWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Người dùng từng từ chối → nói rõ vì sao cần quyền trước khi hỏi lại
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_camera_title)
                    .setMessage(R.string.permission_camera_rationale_photo)
                    .setPositiveButton(R.string.action_allow,
                            (dialog, which) -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA))
                    .setNegativeButton(R.string.action_not_now, null)
                    .show();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /** Tạo file ảnh tạm rồi mở app Camera (Intent ngầm định ACTION_IMAGE_CAPTURE bên trong TakePicture). */
    private void launchCamera() {
        try {
            pendingPhotoUri = ImageStorage.createCameraPhotoUri(this);
            takePictureLauncher.launch(pendingPhotoUri);
        } catch (IOException e) {
            pendingPhotoUri = null;
            showToast(R.string.msg_photo_error);
        } catch (ActivityNotFoundException e) {
            pendingPhotoUri = null;
            showToast(R.string.msg_no_camera_app);
        }
    }

    /** Quyền đã bị tắt hẳn: mở trang thông tin app trong Cài đặt (Intent ngầm định). */
    private void showPermissionBlockedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_camera_title)
                .setMessage(R.string.permission_camera_blocked)
                .setPositiveButton(R.string.action_open_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.action_not_now, null)
                .show();
    }

    // ---------- Danh sách nguyên liệu / bước ----------

    /** Đợi RecyclerView vẽ xong dòng mới (post) rồi đặt con trỏ vào ô nhập đầu tiên của dòng đó. */
    private void focusLastRow(RecyclerView recyclerView) {
        recyclerView.post(() -> {
            RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
            if (adapter == null || adapter.getItemCount() == 0) {
                return;
            }
            RecyclerView.ViewHolder holder =
                    recyclerView.findViewHolderForAdapterPosition(adapter.getItemCount() - 1);
            if (holder instanceof IngredientEditAdapter.IngredientEditViewHolder) {
                ((IngredientEditAdapter.IngredientEditViewHolder) holder).binding.editName.requestFocus();
            } else if (holder instanceof StepEditAdapter.StepEditViewHolder) {
                ((StepEditAdapter.StepEditViewHolder) holder).binding.editContent.requestFocus();
            }
        });
    }

    private void saveRecipe() {
        // Ảnh vừa chọn chưa lưu xong (thường dưới 1 giây) → đợi, tránh lưu món mà thiếu ảnh
        if (viewModel.isPhotoProcessing()) {
            showToast(R.string.msg_photo_processing);
            return;
        }
        String name = textOf(binding.editName);
        if (name.isEmpty()) {
            binding.editName.setError(getString(R.string.error_recipe_name_required));
            binding.editName.requestFocus();
            return;
        }
        int servings = parseInt(textOf(binding.editServings));
        if (servings < MIN_SERVINGS || servings > MAX_SERVINGS) {
            binding.editServings.setError(getString(R.string.error_servings_range, MIN_SERVINGS, MAX_SERVINGS));
            binding.editServings.requestFocus();
            return;
        }

        // Món gốc trong ViewModel (giữ id, yêu thích, ngày tạo...); chưa đọc xong thì chưa cho lưu
        Recipe recipe = viewModel.getRecipe().getValue();
        if (recipe == null) {
            return;
        }
        // Chép dữ liệu form vào món
        recipe.name = name;
        recipe.category = Recipe.CATEGORIES[binding.spinnerCategory.getSelectedItemPosition()];
        recipe.cookTimeMinutes = parseInt(textOf(binding.editCookTime));
        recipe.servings = servings;
        String videoUrl = textOf(binding.editVideoUrl);
        recipe.videoUrl = videoUrl.isEmpty() ? null : videoUrl;
        String note = textOf(binding.editNote);
        recipe.note = note.isEmpty() ? null : note;

        // Nguyên liệu và các bước đã nằm sẵn trong bản nháp của ViewModel (TextWatcher ghi vào khi gõ)
        viewModel.save(recipe);
    }

    private static String textOf(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    /** Ô trống hoặc gõ sai → 0. */
    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
