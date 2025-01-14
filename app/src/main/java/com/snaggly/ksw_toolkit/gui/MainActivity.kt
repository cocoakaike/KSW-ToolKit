package com.snaggly.ksw_toolkit.gui

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.snaggly.ksw_toolkit.BuildConfig
import com.snaggly.ksw_toolkit.R
import com.snaggly.ksw_toolkit.core.service.helper.CoreServiceClient
import com.snaggly.ksw_toolkit.core.service.helper.ServiceAliveCheck
import com.snaggly.ksw_toolkit.gui.viewmodels.MainActivityViewModel
import com.snaggly.ksw_toolkit.util.github.DownloadController
import com.snaggly.ksw_toolkit.util.github.VersionTag

class MainActivity : AppCompatActivity() {
    private val coreServiceClient = CoreServiceClient()
    private lateinit var mainViewModel: MainActivityViewModel
    private lateinit var mainMenuFragment: Fragment
    private var eventManagerFragment: Fragment? = null
    private var systemTweaksFragment: Fragment? = null
    private var adbShellFragment: Fragment? = null
    private var mcuListenerFragment: Fragment? = null
    private var configFragment: Fragment? = null
    private lateinit var mFragManager: FragmentManager
    private lateinit var toolKitPane: Button
    private lateinit var eventManagerPane: Button
    private lateinit var systemTweaksPane: Button
    private lateinit var adbShellPane: Button
    private lateinit var mcuListenerPane: Button
    private lateinit var configImportExportPane: Button
    private lateinit var previousCallingButton: Button
    private lateinit var infoBtn: ImageButton
    private var tabFragmentId = 0

    private lateinit var getContent : ActivityResultLauncher<String>
    private lateinit var createDocument : ActivityResultLauncher<String>


    init {
        var serviceAlive = false
        ServiceAliveCheck.serviceAliveObservers.add {
            serviceAlive = it
            if (it) {
                try {
                    coreServiceClient.connectToService(this)
                } catch (e : Exception) {
                    Toast.makeText(this@MainActivity, "${resources.getText(R.string.unable_to_connect_sevice)}\n$e", Toast.LENGTH_LONG).show()
                }
            } else {
                coreServiceClient.disconnectFromService(this)
            }
        }

        coreServiceClient.clientObservers.add {
            runOnUiThread {
                if (it) {
                    eventManagerPane.isEnabled = true
                    systemTweaksPane.isEnabled = true
                    mcuListenerPane.isEnabled = true
                    configImportExportPane.isEnabled = true
                    try {
                        mainViewModel.initializeServiceOptions(coreServiceClient)
                    } catch (e : Exception) {
                        Toast.makeText(this@MainActivity, "${resources.getText(R.string.unable_to_initialize_sevice)}\n$e", Toast.LENGTH_LONG).show()
                    }
                } else {
                    if (previousCallingButton == eventManagerPane
                        || previousCallingButton == systemTweaksPane
                        || previousCallingButton == mcuListenerPane
                        || previousCallingButton == configImportExportPane) {
                        switchFragment(mFragManager, toolKitPane, mainMenuFragment)
                    }
                    eventManagerPane.isEnabled = false
                    systemTweaksPane.isEnabled = false
                    mcuListenerPane.isEnabled = false
                    configImportExportPane.isEnabled = false
                    if (serviceAlive) {
                        coreServiceClient.connectToService(this)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel = ViewModelProvider(this).get(MainActivityViewModel::class.java)
        if (!checkPermissions())
            finish()
        else {
            startApp()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            window.decorView.windowInsetsController?.hide(navigationBars())
        try{
            ServiceAliveCheck.checkIfServiceIsAlive(this)
        }catch (exception : Exception) {
            Toast.makeText(this, resources.getText(R.string.could_not_check_service), Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkPermissions()
    }

    private fun checkPermissions(): Boolean {
        if (checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(arrayOf(Manifest.permission.INTERNET), 0)
            return false
        }

        return true
    }

    private fun startApp() {
        setContentView(R.layout.activity_main)
        mFragManager = supportFragmentManager
        initViewElements()
        setBtnClicks()
        initPaneFragment()

        getContent = registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it == null)
                return@registerForActivityResult
            try {
                val service = coreServiceClient.coreService ?: throw Exception("Service object missing")
                mainViewModel.importSettings(this, service, it)
            }
            catch (e: Exception) {
                runOnUiThread {
                    e.showExceptionWindow()
                }
            }
        }
        createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument()) {
            if (it == null)
                return@registerForActivityResult
            try {
                val service = coreServiceClient.coreService ?: throw Exception("Service object missing")
                mainViewModel.exportSettings(this, service, it)
            }
            catch (e: Exception) {
                runOnUiThread {
                    e.showExceptionWindow()
                }
            }
        }
    }

    private fun initViewElements() {
        toolKitPane = findViewById(R.id.soundRestorerPaneBtn)
        eventManagerPane = findViewById(R.id.eventManagerPaneBtn)
        systemTweaksPane = findViewById(R.id.systemTweaksPaneBtn)
        adbShellPane = findViewById(R.id.adbShellPaneBtn)
        mcuListenerPane = findViewById(R.id.mcuListenerPaneBtn)
        configImportExportPane = findViewById(R.id.configPaneBtn)
        tabFragmentId = R.id.tabFragment
        infoBtn = findViewById(R.id.mainInfoBtn)
    }

    private fun setBtnClicks() {
        toolKitPane.setOnClickListener { switchFragment(mFragManager, toolKitPane, mainMenuFragment) }
        eventManagerPane.setOnClickListener {
            if (eventManagerFragment == null)
                eventManagerFragment = EventManager(coreServiceClient)
            switchFragment(mFragManager, eventManagerPane, eventManagerFragment!!)
        }
        systemTweaksPane.setOnClickListener {
            if (systemTweaksFragment == null)
                systemTweaksFragment = SystemTwaks(coreServiceClient)
            switchFragment(mFragManager, systemTweaksPane, systemTweaksFragment!!)
        }
        adbShellPane.setOnClickListener {
            if (adbShellFragment == null)
                adbShellFragment = AdbShell()
            switchFragment(mFragManager, adbShellPane, adbShellFragment!!)
        }
        mcuListenerPane.setOnClickListener {
            if (mcuListenerFragment == null)
                mcuListenerFragment = McuListener(coreServiceClient)
            switchFragment(mFragManager, mcuListenerPane, mcuListenerFragment!!)
        }
        configImportExportPane.setOnClickListener {
            if (configFragment == null)
                configFragment = ConfigImportExport(getContent, createDocument)
            switchFragment(mFragManager, configImportExportPane, configFragment!!)
        }
        infoBtn.setOnClickListener {
            showInfoDialog()
        }
    }

    private fun showInfoDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.alertDialogNight)
            .setIcon(R.mipmap.ic_launcher)
            .setTitle("KSW-ToolKit Client")
            .setMessage("Client Version: ${BuildConfig.VERSION_NAME}\n\nDeveloped by @Snaggly\nLicensed in GPLv3.0\n\nFeel free to join our Android Automotive Discord Server!")
            .setPositiveButton(R.string.check_for_updates) { _, _ -> checkClientUpdate() }
            .create()
        dialog.show()
    }

    private fun checkClientUpdate() {
        Thread {
            try {
                val latestGitHubRelease = DownloadController.getClientGitHubRelease()
                val githubVersion = VersionTag.getVersion(latestGitHubRelease.tag_name)
                val installedVersion = VersionTag.getVersion(BuildConfig.VERSION_NAME)
                if (VersionTag.isNewerVersionAvailable(installedVersion, githubVersion)) {
                    runOnUiThread {
                        val dc = DownloadController.draftDownload(this, latestGitHubRelease)
                        if (dc != null) {
                            dc.enqueueDownload()
                        } else {
                            runOnUiThread {
                                Toast.makeText(this, "No APK found! Please download and install manually!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Already up to date!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error in reading URL\r$e", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun initPaneFragment() {
        mainMenuFragment = ToolKit(coreServiceClient)
        previousCallingButton = toolKitPane
        switchFragment(mFragManager, toolKitPane, mainMenuFragment)
        toolKitPane.requestFocus()
    }

    private fun switchFragment(manager: FragmentManager, callingButton: Button, fragment: Fragment) {
        previousCallingButton.setBackgroundResource(R.drawable.btn_selector)
        callingButton.setBackgroundResource(R.drawable.ksw_id7_itme_select_bg_focused)
        previousCallingButton = callingButton
        manager.beginTransaction().replace(tabFragmentId, fragment).commit()
    }

    private fun java.lang.Exception.showExceptionWindow() {
        val alertExc = AlertDialog.Builder(this@MainActivity, R.style.alertDialogNight).setTitle("KSW-ToolKit")
            .setMessage("$this").create()
        alertExc.show()
    }
}

