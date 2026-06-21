package com.abuzahra.admin.ui.fragments

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.MainActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.api.User
import com.abuzahra.admin.databinding.FragmentUsersBinding
import com.abuzahra.admin.ui.adapters.UserAdapter
import com.abuzahra.admin.ui.dashboard.DashboardViewModel
import com.abuzahra.admin.ui.dashboard.DashboardViewModelFactory
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Users fragment — functional copy of the web's UsersView (admin only):
 *  - User list (RecyclerView): avatar + username + role chip + email + created date
 *  - Create user dialog (username/email/password/role)
 *  - Delete user (with confirmation)
 *  - Regenerate permanent code (dialog showing new code)
 *  - Admin badge on admins
 *  - Refresh button + pull-to-refresh
 *  - Non-admins see "ليس لديك صلاحية الوصول"
 */
class UsersFragment : BaseFragment() {

    private var _binding: FragmentUsersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels {
        DashboardViewModelFactory(Preferences.getInstance(requireContext()))
    }

    private lateinit var prefs: Preferences
    private val userAdapter: UserAdapter by lazy {
        UserAdapter(prefs.userId) { user -> confirmDelete(user) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Preferences.getInstance(requireContext())

        // Non-admins see access denied
        if (prefs.userRole != "admin") {
            showAccessDenied()
            return
        }

        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = userAdapter
        }

        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        binding.btnAdd.setOnClickListener { showCreateUserDialog() }
        binding.usersSwipe.setOnRefreshListener { viewModel.refresh() }

        observeViewModel()
    }

    private fun showAccessDenied() {
        binding.rvUsers.visibility = View.GONE
        binding.btnAdd.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        // Replace empty state message to indicate access-denied
        val childCount = binding.emptyState.childCount
        if (childCount >= 2) {
            (binding.emptyState.getChildAt(0) as? TextView)?.text = "🔒"
            (binding.emptyState.getChildAt(1) as? TextView)?.text =
                "ليس لديك صلاحية الوصول\nهذا القسم متاح للمسؤولين فقط"
        }
    }

    private fun observeViewModel() {
        viewModel.users.observe(viewLifecycleOwner) { result ->
            binding.usersSwipe.isRefreshing = false
            when (result) {
                is Result.Loading -> Unit
                is Result.Success -> {
                    val users = result.data
                    userAdapter.submitList(users)
                    binding.emptyState.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvUsers.visibility = if (users.isEmpty()) View.GONE else View.VISIBLE
                    binding.tvCount.text = users.size.toString()
                    binding.tvAdminCount.text = users.count { it.role == "admin" }.toString()
                }
                is Result.Error -> {
                    (activity as? MainActivity)?.showSnack(result.message)
                }
            }
        }

        viewModel.userActionResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            when (result) {
                is Result.Success -> {
                    (activity as? MainActivity)?.showSnack(result.data)
                }
                is Result.Error -> {
                    (activity as? MainActivity)?.showSnack(result.message)
                }
                else -> Unit
            }
            viewModel.consumeUserActionResult()
        }

        viewModel.regenerateResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            when (result) {
                is Result.Success -> showCodeDialog(result.data)
                is Result.Error -> {
                    (activity as? MainActivity)?.showSnack(result.message)
                }
                else -> Unit
            }
            viewModel.consumeRegenerateResult()
        }
    }

    // ── Create user dialog ────────────────────────────────────────
    private fun showCreateUserDialog() {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val etUsername = EditText(context).apply {
            hint = "اسم المستخدم *"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(context.getColor(R.color.text_primary))
            setHintTextColor(context.getColor(R.color.text_hint))
        }
        container.addView(etUsername)

        val etEmail = EditText(context).apply {
            hint = "البريد الإلكتروني"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(context.getColor(R.color.text_primary))
            setHintTextColor(context.getColor(R.color.text_hint))
        }
        val lp1 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp1.bottomMargin = 16
        etEmail.layoutParams = lp1
        container.addView(etEmail)

        val etPassword = EditText(context).apply {
            hint = "كلمة المرور *"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(context.getColor(R.color.text_primary))
            setHintTextColor(context.getColor(R.color.text_hint))
        }
        val lp2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp2.bottomMargin = 16
        etPassword.layoutParams = lp2
        container.addView(etPassword)

        val tvRoleLabel = TextView(context).apply {
            text = "الدور:"
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 13f
        }
        container.addView(tvRoleLabel)
        val spRole = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("viewer", "admin")
            )
        }
        container.addView(spRole)

        MaterialAlertDialogBuilder(context)
            .setTitle("👤 إنشاء مستخدم جديد")
            .setView(container)
            .setPositiveButton("إنشاء") { _, _ ->
                val username = etUsername.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString()
                val role = spRole.selectedItem?.toString() ?: "viewer"

                if (username.isBlank() || password.isBlank()) {
                    (activity as? MainActivity)?.showSnack("اسم المستخدم وكلمة المرور مطلوبة")
                    return@setPositiveButton
                }
                viewModel.createUser(username, password, email, role)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Delete user ───────────────────────────────────────────────
    private fun confirmDelete(user: User) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚠️ حذف المستخدم")
            .setMessage(
                "هل أنت متأكد من حذف هذا المستخدم؟\n\n" +
                        "الاسم: ${user.username}\n" +
                        "البريد: ${user.email}\n\n" +
                        "لا يمكن التراجع عن هذا الإجراء."
            )
            .setPositiveButton("حذف") { _, _ -> viewModel.deleteUser(user.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Regenerate permanent code dialog ──────────────────────────
    private fun showCodeDialog(code: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔑 كود الربط الجديد")
            .setMessage("الكود الدائم الجديد:\n\n$code\n\nاحفظ هذا الكود — لن يظهر مرة أخرى.")
            .setPositiveButton("نسخ") { _, _ ->
                val clipboard = requireContext()
                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("permanent_code", code)
                )
                (activity as? MainActivity)?.showSnack(getString(R.string.copied_code))
            }
            .setNegativeButton(R.string.ok, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.userRole == "admin") viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
