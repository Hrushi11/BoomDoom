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
  description = "film (OpenAI-directed), stickman (Stickman Studio) or placeholder (ffmpeg only)."
  default     = "film"
}

variable "github_owner" {
  type        = string
  description = "GitHub user/org that owns the repo; the image is ghcr.io/<owner>/boomdoom-backend."
  default     = "Hrushi11"
}

variable "ghcr_token" {
  type        = string
  sensitive   = true
  description = "Classic GitHub token with read:packages (host pulls the private image). Set it, then re-apply."
  default     = "unset"
}
