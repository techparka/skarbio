package com.budgetapp.ocr

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budgetapp.ocr.data.local.AppDatabase
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BillReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "bill_reminders"
        const val WORK_NAME = "bill_reminder_check"
    }

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val billDao = database.billDao()

            val billsWithReminders = billDao.getBillsWithReminders()
            val today = LocalDate.now()

            createNotificationChannel()

            var notificationId = 1000

            for (bill in billsWithReminders) {
                try {
                    val dueDate = LocalDate.parse(bill.dueDate)
                    val daysUntilDue = ChronoUnit.DAYS.between(today, dueDate)

                    if (daysUntilDue <= bill.reminderDaysBefore) {
                        val amountStr = String.format(
                            applicationContext.getString(R.string.amount_format),
                            bill.amount
                        )
                        val title: String
                        val message: String

                        when {
                            daysUntilDue < 0 -> {
                                title = applicationContext.getString(R.string.notification_overdue_title)
                                message = applicationContext.getString(
                                    R.string.notification_overdue_message,
                                    bill.vendorName, amountStr, -daysUntilDue
                                )
                            }
                            daysUntilDue == 0L -> {
                                title = applicationContext.getString(R.string.notification_due_today_title)
                                message = applicationContext.getString(
                                    R.string.notification_due_today_message,
                                    bill.vendorName, amountStr
                                )
                            }
                            daysUntilDue == 1L -> {
                                title = applicationContext.getString(R.string.notification_due_tomorrow_title)
                                message = applicationContext.getString(
                                    R.string.notification_due_tomorrow_message,
                                    bill.vendorName, amountStr
                                )
                            }
                            else -> {
                                title = applicationContext.getString(
                                    R.string.notification_due_days_title,
                                    daysUntilDue
                                )
                                message = applicationContext.getString(
                                    R.string.notification_due_days_message,
                                    bill.vendorName, amountStr, bill.dueDate
                                )
                            }
                        }

                        sendNotification(notificationId++, title, message)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BillReminder", "Error processing bill ${bill.id}", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("BillReminder", "Worker failed", e)
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_bills),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_description)
            }

            val notificationManager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(id: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bill)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }
}
