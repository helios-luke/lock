package com.wifilocker

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest
import java.util.Calendar

data class Account(
    val name: String,
    val ssid: String,
    val password: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var ssidText: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AccountsAdapter
    private val gson = Gson()
    private val accounts = mutableListOf<Account>()

    private val PREFS_NAME = "wifilocker_prefs"
    private val KEY_ACCOUNTS = "accounts"
    private val PERM_REQ = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ssidText = findViewById(R.id.ssidText)
        recycler = findViewById(R.id.accountsList)
        val addBtn: Button = findViewById(R.id.addBtn)

        loadAccounts()
        adapter = AccountsAdapter(accounts) { account -> onAccountClicked(account) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        addBtn.setOnClickListener { showAddDialog() }

        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateSsidDisplay()
    }

    private fun requestPermissionsIfNeeded() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQ)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateSsidDisplay()
    }

    private fun getCurrentSsid(): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo ?: return null
        var ssid = info.ssid ?: return null
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        if (ssid.isEmpty() || ssid == "<unknown ssid>") return null
        return ssid
    }

    private fun updateSsidDisplay() {
        val ssid = getCurrentSsid()
        ssidText.text = "Connected Wi-Fi: " + (ssid ?: "(none)")
    }

    private fun loadAccounts() {
        val json = prefs.getString(KEY_ACCOUNTS, null)
        accounts.clear()
        if (json != null) {
            val type = object : TypeToken<MutableList<Account>>() {}.type
            val loaded: MutableList<Account> = gson.fromJson(json, type)
            accounts.addAll(loaded)
        }
    }

    private fun saveAccounts() {
        prefs.edit().putString(KEY_ACCOUNTS, gson.toJson(accounts)).apply()
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_account, null)
        val nameInput: EditText = view.findViewById(R.id.inputName)
        val ssidInput: EditText = view.findViewById(R.id.inputSsid)
        val passInput: EditText = view.findViewById(R.id.inputPassword)

        AlertDialog.Builder(this)
            .setTitle("Add New Account")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val ssid = ssidInput.text.toString().trim()
                val pass = passInput.text.toString()
                if (name.isNotEmpty() && ssid.isNotEmpty() && pass.isNotEmpty()) {
                    accounts.add(Account(name, ssid, pass))
                    saveAccounts()
                    adapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onAccountClicked(account: Account) {
        val currentSsid = getCurrentSsid()
        if (currentSsid == null || currentSsid != account.ssid) {
            Toast.makeText(this, "Not connected to this Wi-Fi network.", Toast.LENGTH_SHORT).show()
            return
        }
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val timeStr = "$y-$m-$d-$h"
        val raw = "$currentSsid:${account.password}:$timeStr"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        val generated = hex.substring(0, 12)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Generated Password")
            .setMessage(generated)
            .setPositiveButton("Copy") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("password", generated))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .create()
        dialog.show()
    }
}

class AccountsAdapter(
    private val items: List<Account>,
    private val onClick: (Account) -> Unit
) : RecyclerView.Adapter<AccountsAdapter.VH>() {

    class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val title: TextView = root.findViewById(R.id.itemTitle)
        val sub: TextView = root.findViewById(R.id.itemSub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false) as LinearLayout
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = items[position]
        holder.title.text = a.name
        holder.sub.text = "SSID: ${a.ssid}"
        holder.root.setOnClickListener { onClick(a) }
    }

    override fun getItemCount(): Int = items.size
}
