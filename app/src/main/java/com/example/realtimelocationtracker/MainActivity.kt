package com.example.realtimelocationtracker

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.realtimelocationtracker.model.User
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.firebase.iid.FirebaseInstanceId
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.paperdb.Paper

class MainActivity : AppCompatActivity() {

    companion object {
        private val MY_REQUEST_CODE = 7117
    }

    lateinit var user_information: DatabaseReference
    lateinit var providers: List<AuthUI.IdpConfig>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Paper.init(this)

        //Init Firebase
        user_information = FirebaseDatabase.getInstance().getReference(Common.USER_INFORMATION)

        //Init Provider
        providers = listOf<AuthUI.IdpConfig>(
                AuthUI.IdpConfig.EmailBuilder().build(),
                AuthUI.IdpConfig.GoogleBuilder().build()
        )

        //Request permission
        Dexter.withContext(this)
                .withPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(object : PermissionListener {
                    override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                        showSignInOptions()
                    }

                    override fun onPermissionRationaleShouldBeShown(p0: PermissionRequest?, p1: PermissionToken?) {
                    }

                    override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                        Toast.makeText(this@MainActivity, "You must accept this permission", Toast.LENGTH_SHORT).show()
                    }

                }).check()
    }

    private fun showSignInOptions() {
        startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build(), MY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MY_REQUEST_CODE) {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            //Check if user exists on database
            user_information.orderByKey()
                    .equalTo(firebaseUser!!.uid)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onCancelled(p0: DatabaseError) {

                        }

                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            if (dataSnapshot.value == null) {
                                //User not exists
                                if (!dataSnapshot.child(firebaseUser!!.uid).exists()) {
                                    Common.loggedUser = User(firebaseUser.uid, firebaseUser.email!!)

                                    //Add user to database
                                    user_information.child(Common.loggedUser.uid!!)
                                            .setValue(Common.loggedUser)
                                }
                            } else {

                                //User available
                                Common.loggedUser = dataSnapshot.child(firebaseUser.uid)
                                        .getValue(User::class.java)!!
                            }

                            //Save UID to storage to update location from killed mode
                            Paper.book().write(Common.USER_UID_SAVE_KEY, Common.loggedUser.uid)
                            updateToken(firebaseUser)
                            setupUI()
                        }

                    })
        }
    }

    private fun setupUI() {
        //After all done Navigation Home
        startActivity(Intent(this, HomeActivity::class.java))
    }

    private fun updateToken(firebaseUser: FirebaseUser) {
        val tokens = FirebaseDatabase.getInstance().getReference(Common.TOKEN)

        //Get token
        FirebaseInstanceId.getInstance().instanceId
                .addOnSuccessListener { instanceIdResult ->
                    tokens.child(firebaseUser!!.uid)
                            .setValue(instanceIdResult.token)
                }
                .addOnFailureListener { e -> Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show() }

    }
}