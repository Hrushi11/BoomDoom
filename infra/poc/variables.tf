variable "region" {
  type    = string
  default = "ap-south-1" # Mumbai (Tech Spec §1.2)
}

variable "instance_type" {
  type        = string
  description = "Runs Postgres, API, workers and rendering. Rendering is ~2x real time on 2 vCPU."
  default     = "c7i.xlarge" # 4 vCPU / 8 GB
}

variable "root_volume_gb" {
  type    = number
  default = 40
}

variable "domain_name" {
  type        = string
  description = "API hostname. Leave empty to use <elastic-ip>.sslip.io (HTTPS via Let's Encrypt, no DNS setup)."
  default     = ""
}

variable "openai_api_key" {
  type      = string
  sensitive = true
}

variable "openai_daily_cost_cap_usd" {
  type    = number
  default = 10
}

variable "renderer" {
  type        = string
  description = "placeholder (ffmpeg only) or stickman (requires the fork baked into the image)."
  default     = "placeholder"
}
