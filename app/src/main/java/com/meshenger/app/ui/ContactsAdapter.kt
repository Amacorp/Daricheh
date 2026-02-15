package com.daricheh.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.daricheh.app.databinding.ItemContactBinding
import com.daricheh.app.model.Contact

class ContactsAdapter(
    private val onClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

    private val contacts = mutableListOf<Contact>()

    fun submitList(list: List<Contact>) {
        contacts.clear()
        contacts.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount() = contacts.size

    inner class ViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.tvContactName.text = contact.name
            binding.tvContactPhone.text = contact.phoneNumber
            binding.tvContactInitial.text = contact.name.firstOrNull()?.uppercase() ?: "?"

            if (contact.hasApp) {
                binding.tvHasApp.visibility = View.VISIBLE
                binding.tvHasApp.text = "✓ مش مسنجر"
                binding.root.alpha = 1.0f
            } else {
                binding.tvHasApp.visibility = View.VISIBLE
                binding.tvHasApp.text = "دعوت کنید"
                binding.root.alpha = 0.6f
            }

            binding.root.setOnClickListener { onClick(contact) }
        }
    }
}