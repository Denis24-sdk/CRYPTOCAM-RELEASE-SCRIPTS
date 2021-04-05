package com.tnibler.cryptocam.keys

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tnibler.cryptocam.databinding.KeyItemBinding

class KeyAdapter(val onItemClick: (KeyItem, Boolean) -> Unit) :
    ListAdapter<KeyItem, KeyAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(private val binding: KeyItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(keyItem: KeyItem, onItemClick: (KeyItem, Boolean) -> Unit) {
            with(binding) {
                keyItemNameView.text = keyItem.recipient.name
                keyItemKeyView.text = keyItem.fingerprint
                keyItemCheckbox.isChecked = keyItem.isSelected
                keyItemCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
                    onItemClick(keyItem, isChecked)
                }
                keyItemRoot.setOnClickListener {
                    keyItemCheckbox.isChecked = !keyItemCheckbox.isChecked
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = KeyItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<KeyItem>() {
            override fun areItemsTheSame(oldItem: KeyItem, newItem: KeyItem): Boolean {
                return oldItem == newItem // keys aren't going to change in content so this is fine
            }

            override fun areContentsTheSame(oldItem: KeyItem, newItem: KeyItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}

