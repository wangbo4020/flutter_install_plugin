package com.zaihui.installplugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.File
import java.io.FileNotFoundException

/**
 * 1 获取Registrar 这个接口可以获取 context
 * 2 添加自身所需依赖
 * @property registrar Registrar
 * @constructor
 */
class InstallPlugin() : FlutterPlugin, ActivityAware, MethodCallHandler {
    private var apkFile: File? = null

    companion object {
        private const val REQ_INSTALL = 1234
        private const val CHANNEL_NAME = "com.zaihui/install_plugin"

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), CHANNEL_NAME)
            val installPlugin = InstallPlugin()
            channel.setMethodCallHandler(installPlugin)
            registrar.addActivityResultListener { requestCode, resultCode, intent ->
                Log.d(
                        "ActivityResultListener",
                        "requestCode=$requestCode, resultCode = $resultCode, intent = $intent"
                )
                if (resultCode == Activity.RESULT_OK && requestCode == REQ_INSTALL) {
                    installPlugin.install24(registrar.context(), installPlugin.apkFile)
                    true
                } else

                    false
            }
        }
    }

    private var mContext: Activity? = null
    private var mMethodChannel: MethodChannel? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {

        mMethodChannel = MethodChannel(binding.binaryMessenger, CHANNEL_NAME)
        mMethodChannel!!.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        mMethodChannel?.setMethodCallHandler(null)
        mMethodChannel = null

    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        mContext = binding.activity
        binding.addActivityResultListener { requestCode, resultCode, data ->
            Log.d("ActivityResultListener", "requestCode=$requestCode, resultCode = $resultCode, intent = $data")
            if (resultCode == Activity.RESULT_OK && requestCode == REQ_INSTALL) {
                install24(mContext!!, apkFile)
                true
            } else
                false
        }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onDetachedFromActivity() {
        mContext = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "installApk" -> {
                val filePath = call.argument<String>("filePath")
                val appId = call.argument<String>("appId")
                Log.d("android plugin", "installApk $filePath $appId")
                try {
                    installApk(filePath, appId)
                    result.success("Success")
                } catch (e: Throwable) {
                    result.error(e.javaClass.simpleName, e.message, null)
                }
            }
            else -> result.notImplemented()
        }
    }

    private fun installApk(filePath: String?, appId: String?) {
        if (filePath == null) throw NullPointerException("fillPath is null!")
        val activity: Activity = mContext ?: throw NullPointerException("context is null!")

        val file = File(filePath)
        if (!file.exists()) throw FileNotFoundException("$filePath is not exist! or check permission")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (canRequestPackageInstalls(activity)) install24(activity, file)
            else {
                showSettingPackageInstall(activity)
                this.apkFile = file
            }
        } else {
            installBelow24(activity, file)
        }
    }


    private fun showSettingPackageInstall(activity: Activity) { // todo to test with android 26
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("SettingPackageInstall", ">= Build.VERSION_CODES.O")
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = Uri.parse("package:" + activity.packageName)
            activity.startActivityForResult(intent, REQ_INSTALL)
        } else {
            throw RuntimeException("VERSION.SDK_INT < O")
        }

    }

    private fun canRequestPackageInstalls(activity: Activity): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()
    }

    private fun installBelow24(context: Context, file: File?) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val uri = Uri.fromFile(file)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        context.startActivity(intent)
    }

    /**
     * android24及以上安装需要通过 ContentProvider 获取文件Uri，
     * 需在应用中的AndroidManifest.xml 文件添加 provider 标签，
     * 并新增文件路径配置文件 res/xml/provider_path.xml
     * 在android 6.0 以上如果没有动态申请文件读写权限，会导致文件读取失败，你将会收到一个异常。
     * 插件中不封装申请权限逻辑，是为了使模块功能单一，调用者可以引入独立的权限申请插件
     */
    private fun install24(context: Context?, file: File?) {
        if (context == null) throw NullPointerException("context is null!")
        if (file == null) throw NullPointerException("file is null!")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileProvider.install", file)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        context.startActivity(intent)
    }
}
