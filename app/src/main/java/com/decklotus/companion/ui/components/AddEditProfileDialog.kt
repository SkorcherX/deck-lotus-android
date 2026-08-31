package com.decklotus.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.decklotus.companion.data.UserProfile
import com.decklotus.companion.ui.theme.*

@Composable
fun AddEditProfileDialog(
    profileToEdit: UserProfile? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, token: String) -> Unit
) {
    var name by remember { mutableStateOf(profileToEdit?.name ?: "") }
    var token by remember { mutableStateOf(profileToEdit?.apiToken ?: "") }
    val isEdit = profileToEdit != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161B22),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = LotusPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (isEdit) "Edit Profile" else "Add Family Member",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "Each family member's cards will commit directly to their individual Deck Lotus collection.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Sean, Alex, Mom") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LotusCyan,
                        unfocusedBorderColor = SurfaceBorder
                    )
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Deck Lotus API Key / Token") },
                    placeholder = { Text("Paste API key from user profile") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LotusCyan,
                        unfocusedBorderColor = SurfaceBorder
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && token.isNotBlank()) {
                                onSave(name.trim(), token.trim())
                            }
                        },
                        enabled = name.isNotBlank() && token.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = LotusPurple)
                    ) {
                        Text(if (isEdit) "Save Changes" else "Add Member", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}