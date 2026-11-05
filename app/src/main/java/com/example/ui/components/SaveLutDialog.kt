package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

@Composable
fun SaveLutDialog(
    onDismiss: () -> Unit,
    onSaveLut: (name: String, description: String) -> Unit
) {
    var lutName by remember { mutableStateOf("My S24 Look") }
    var lutDescription by remember { mutableStateOf("Custom grade with contrast & warm skin tones") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("save_lut_dialog"),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioCardBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = ResolveAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CREATE CUSTOM 3D LUT",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = "Bake current color wheels, curves, exposure, and tone mappings into a reusable 3D LUT profile.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Name Input
                OutlinedTextField(
                    value = lutName,
                    onValueChange = { lutName = it },
                    label = { Text("LUT Name", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ResolveAmber,
                        unfocusedBorderColor = StudioCardBorder,
                        focusedLabelColor = ResolveAmber,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = ResolveAmber
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lut_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Description Input
                OutlinedTextField(
                    value = lutDescription,
                    onValueChange = { lutDescription = it },
                    label = { Text("Description & Notes", fontSize = 11.sp) },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ResolveAmber,
                        unfocusedBorderColor = StudioCardBorder,
                        focusedLabelColor = ResolveAmber,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = ResolveAmber
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lut_description_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextMuted, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (lutName.isNotBlank()) {
                                onSaveLut(lutName.trim(), lutDescription.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ResolveAmber),
                        shape = RoundedCornerShape(8.dp),
                        enabled = lutName.isNotBlank(),
                        modifier = Modifier.testTag("confirm_save_lut_button")
                    ) {
                        Text("Save to Library", color = Color(0xFF1E1400), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
