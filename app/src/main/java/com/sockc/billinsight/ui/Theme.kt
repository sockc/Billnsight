package com.sockc.billinsight.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary=Color(0xFF326BFA),
    onPrimary=Color.White,
    primaryContainer=Color(0xFFE9F1FF),
    onPrimaryContainer=Color(0xFF244AB1),
    secondary=Color(0xFF7660EA),
    onSecondary=Color.White,
    secondaryContainer=Color(0xFFF0EAFE),
    onSecondaryContainer=Color(0xFF6547C5),
    background=Color(0xFFF6F8FC),
    onBackground=Color(0xFF151D38),
    surface=Color.White,
    onSurface=Color(0xFF18223B),
    surfaceVariant=Color(0xFFF0F3FA),
    onSurfaceVariant=Color(0xFF77829B),
    outline=Color(0xFF9AA8B3),
    outlineVariant=Color(0xFFE8EDF5),
    error=Color(0xFFBA3441),
    errorContainer=Color(0xFFFFE7E8),
)

private val Dark = darkColorScheme(
    primary=Color(0xFF86AEFF),
    onPrimary=Color(0xFF10244F),
    primaryContainer=Color(0xFF253B70),
    onPrimaryContainer=Color(0xFFE2EAFF),
    secondary=Color(0xFFA9C9F4),
    onSecondary=Color(0xFF1A3558),
    secondaryContainer=Color(0xFF29415E),
    onSecondaryContainer=Color(0xFFDBE7FB),
    background=Color(0xFF101A24),
    onBackground=Color(0xFFE8EEF4),
    surface=Color(0xFF192734),
    onSurface=Color(0xFFE8EEF4),
    surfaceVariant=Color(0xFF233543),
    onSurfaceVariant=Color(0xFFAEBFC9),
    outline=Color(0xFF728695),
    outlineVariant=Color(0xFF314453),
    error=Color(0xFFFF9E9E),
    errorContainer=Color(0xFF5D252B),
)

private val BillTypography=Typography(
    headlineLarge=TextStyle(fontSize=34.sp,fontWeight=FontWeight.Bold,lineHeight=42.sp),
    headlineMedium=TextStyle(fontSize=29.sp,fontWeight=FontWeight.Bold,lineHeight=36.sp),
    headlineSmall=TextStyle(fontSize=25.sp,fontWeight=FontWeight.Bold,lineHeight=32.sp),
    titleLarge=TextStyle(fontSize=20.sp,fontWeight=FontWeight.SemiBold,lineHeight=28.sp),
    titleMedium=TextStyle(fontSize=17.sp,fontWeight=FontWeight.SemiBold,lineHeight=24.sp),
    titleSmall=TextStyle(fontSize=15.sp,fontWeight=FontWeight.SemiBold,lineHeight=22.sp),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
    bodySmall=TextStyle(fontSize=12.sp,lineHeight=19.sp),
    labelLarge=TextStyle(fontSize=14.sp,fontWeight=FontWeight.SemiBold,lineHeight=20.sp),
    labelMedium=TextStyle(fontSize=12.sp,fontWeight=FontWeight.Medium,lineHeight=18.sp),
    labelSmall=TextStyle(fontSize=11.sp,fontWeight=FontWeight.Medium,lineHeight=16.sp),
)

private val BillShapes=Shapes(
    extraSmall=RoundedCornerShape(8.dp),
    small=RoundedCornerShape(12.dp),
    medium=RoundedCornerShape(16.dp),
    large=RoundedCornerShape(22.dp),
    extraLarge=RoundedCornerShape(28.dp),
)

@Composable
fun BillInsightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme=if (isSystemInDarkTheme()) Dark else Light,
        typography=BillTypography,
        shapes=BillShapes,
        content=content,
    )
}
