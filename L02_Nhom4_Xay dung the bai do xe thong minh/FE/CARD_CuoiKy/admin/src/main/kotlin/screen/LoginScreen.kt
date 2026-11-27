package screen

import Navigator
import Screen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import viewmodel.LoginScreenModel

@Composable
fun LoginScreen(
    screenModel: LoginScreenModel,
    navigator: Navigator
) {
    val loginState by screenModel.loginState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ui.ParkColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ui.ParkColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PC",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Quản trị Thẻ Công Viên",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ui.ParkColors.TextPrimary
            )

            Text(
                text = "Hệ thống quản lý thẻ công viên thông minh",
                fontSize = 14.sp,
                color = ui.ParkColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Login Card
            ui.AppCard(
                modifier = Modifier.width(440.dp),
                padding = 32.dp
            ) {
                Column {
                    Text(
                        text = "Đăng nhập",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = ui.ParkColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = screenModel.username,
                        onValueChange = { screenModel.username = it },
                        label = { Text("Tên đăng nhập") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ui.lightTextFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = screenModel.password,
                        onValueChange = { screenModel.password = it },
                        label = { Text("Mật khẩu") },
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(
                                    if (passwordVisible) "Ẩn" else "Hiện",
                                    color = ui.ParkColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ui.lightTextFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { screenModel.login() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = ui.ParkColors.Primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        enabled = loginState !is viewmodel.LoginUiState.Loading,
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        if (loginState is viewmodel.LoginUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Đăng nhập",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    if (loginState is viewmodel.LoginUiState.Error) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = (loginState as viewmodel.LoginUiState.Error).msg,
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Login success: điều hướng màn khác hoặc show thông tin
            if (loginState is viewmodel.LoginUiState.Success) {
                LaunchedEffect(Unit) {
                    navigator.navigateTo(Screen.ConnectCard)
                }
            }
        }
    }
}