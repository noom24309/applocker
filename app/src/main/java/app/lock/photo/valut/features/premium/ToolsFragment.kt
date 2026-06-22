package app.lock.photo.valut.features.premium

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.lock.photo.valut.databinding.FragmentToolsBinding
import app.lock.photo.valut.features.cleanup.duplicates.DuplicateFinderActivity
import app.lock.photo.valut.features.cleanup.health.VaultHealthActivity
import app.lock.photo.valut.features.cleanup.largefiles.LargeFilesActivity
import app.lock.photo.valut.features.cleanup.smartcleanup.SmartCleanupActivity
import app.lock.photo.valut.features.cleanup.storage.StorageAnalyzerActivity
import app.lock.photo.valut.features.documents.PrivateDocumentsActivity
import app.lock.photo.valut.features.premium.notes.PrivateNotesActivity
import app.lock.photo.valut.features.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint

/** Tools tab — cleanup + security tools, hosted inside [app.lock.photo.valut.features.home.MainActivity]. */
@AndroidEntryPoint
class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSettings.setOnClickListener {
            startActivity(SettingsActivity.intent(requireContext()))
        }

        // Cleanup tools.
        binding.cardDuplicates.setOnClickListener { startActivity(DuplicateFinderActivity.intent(requireContext())) }
        binding.cardLargeFiles.setOnClickListener { startActivity(LargeFilesActivity.intent(requireContext())) }
        binding.cardSmartCleanup.setOnClickListener { startActivity(SmartCleanupActivity.intent(requireContext())) }
        binding.cardStorage.setOnClickListener { startActivity(StorageAnalyzerActivity.intent(requireContext())) }

        // Security & privacy.
        binding.cardHealth.setOnClickListener { startActivity(VaultHealthActivity.intent(requireContext())) }

        // Private storage.
        binding.cardPrivateNotes.setOnClickListener { startActivity(PrivateNotesActivity.intent(requireContext())) }
        binding.cardPrivateDocuments.setOnClickListener { startActivity(PrivateDocumentsActivity.intent(requireContext())) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
