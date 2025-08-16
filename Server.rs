# Encrypted Messaging Server in Rust

## Cargo.toml
```toml
[package]
name = "encrypted-messaging-server"
version = "0.1.0"
edition = "2021"

[dependencies]
tokio = { version = "1.0", features = ["full"] }
tokio-tungstenite = "0.20"
futures = "0.3"
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
uuid = { version = "1.0", features = ["v4", "serde"] }
aes-gcm = "0.10"
rand = "0.8"
base64 = "0.21"
sha2 = "0.10"
hmac = "0.12"
argon2 = "0.5"
rsa = "0.9"
x25519-dalek = "2.0"
ed25519-dalek = "2.0"
tracing = "0.1"
tracing-subscriber = "0.3"
dashmap = "5.0"
chrono = { version = "0.4", features = ["serde"] }
clap = { version = "4.0", features = ["derive"] }
```

## src/main.rs
```rust
use std::net::SocketAddr;
use tokio::net::{TcpListener, TcpStream};
use tokio_tungstenite::{accept_async, tungstenite::Message, WebSocketStream};
use futures::{SinkExt, StreamExt};
use tracing::{info, error, warn};
use clap::Parser;

mod server;
mod crypto;
mod models;
mod handlers;

use server::Server;

#[derive(Parser, Debug)]
#[command(name = "encrypted-messaging-server")]
#[command(about = "A secure encrypted messaging server")]
struct Args {
    #[arg(short, long, default_value = "127.0.0.1:8080")]
    bind: SocketAddr,
    
    #[arg(short, long, default_value = "info")]
    log_level: String,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();
    
    // Initialize logging
    tracing_subscriber::fmt()
        .with_max_level(args.log_level.parse().unwrap_or(tracing::Level::INFO))
        .init();

    // Create server instance
    let server = Server::new().await?;
    
    // Bind to address
    let listener = TcpListener::bind(&args.bind).await?;
    info!("🚀 Encrypted messaging server running on {}", args.bind);
    
    // Accept connections
    while let Ok((stream, addr)) = listener.accept().await {
        info!("New connection from: {}", addr);
        let server_clone = server.clone();
        
        tokio::spawn(async move {
            if let Err(e) = handle_connection(stream, server_clone).await {
                error!("Connection error: {}", e);
            }
        });
    }
    
    Ok(())
}

async fn handle_connection(
    stream: TcpStream,
    server: Server,
) -> Result<(), Box<dyn std::error::Error>> {
    let ws_stream = accept_async(stream).await?;
    server.handle_client(ws_stream).await?;
    Ok(())
}
```

## src/server.rs
```rust
use std::sync::Arc;
use tokio::sync::{RwLock, mpsc};
use tokio_tungstenite::{WebSocketStream, tungstenite::Message};
use futures::{SinkExt, StreamExt};
use serde_json;
use uuid::Uuid;
use dashmap::DashMap;
use tracing::{info, error, warn};

use crate::models::*;
use crate::handlers::MessageHandler;
use crate::crypto::CryptoManager;

#[derive(Clone)]
pub struct Server {
    clients: Arc<DashMap<Uuid, ClientConnection>>,
    rooms: Arc<DashMap<String, Room>>,
    crypto_manager: Arc<CryptoManager>,
    message_handler: Arc<MessageHandler>,
}

pub struct ClientConnection {
    pub user_id: Uuid,
    pub username: String,
    pub public_key: Vec<u8>,
    pub sender: mpsc::UnboundedSender<Message>,
    pub is_authenticated: bool,
}

impl Server {
    pub async fn new() -> Result<Self, Box<dyn std::error::Error>> {
        let crypto_manager = Arc::new(CryptoManager::new()?);
        let message_handler = Arc::new(MessageHandler::new());
        
        Ok(Self {
            clients: Arc::new(DashMap::new()),
            rooms: Arc::new(DashMap::new()),
            crypto_manager,
            message_handler,
        })
    }

    pub async fn handle_client(
        &self,
        mut ws_stream: WebSocketStream<tokio::net::TcpStream>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let (mut ws_sender, mut ws_receiver) = ws_stream.split();
        let (tx, mut rx) = mpsc::unbounded_channel();
        
        let client_id = Uuid::new_v4();
        info!("Client {} connected", client_id);

        // Spawn task to handle outgoing messages
        let tx_clone = tx.clone();
        tokio::spawn(async move {
            while let Some(msg) = rx.recv().await {
                if ws_sender.send(msg).await.is_err() {
                    break;
                }
            }
        });

        // Handle incoming messages
        while let Some(msg) = ws_receiver.next().await {
            match msg {
                Ok(Message::Text(text)) => {
                    if let Err(e) = self.handle_message(client_id, text, &tx).await {
                        error!("Error handling message: {}", e);
                    }
                }
                Ok(Message::Close(_)) => {
                    info!("Client {} disconnected", client_id);
                    break;
                }
                Err(e) => {
                    error!("WebSocket error: {}", e);
                    break;
                }
                _ => {}
            }
        }

        // Clean up client connection
        self.cleanup_client(client_id).await;
        Ok(())
    }

    async fn handle_message(
        &self,
        client_id: Uuid,
        message: String,
        sender: &mpsc::UnboundedSender<Message>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let request: ClientMessage = serde_json::from_str(&message)?;
        
        match request.message_type.as_str() {
            "register" => self.handle_register(client_id, request, sender).await,
            "authenticate" => self.handle_authenticate(client_id, request, sender).await,
            "send_message" => self.handle_send_message(client_id, request).await,
            "create_room" => self.handle_create_room(client_id, request, sender).await,
            "join_room" => self.handle_join_room(client_id, request, sender).await,
            "get_messages" => self.handle_get_messages(client_id, request, sender).await,
            _ => {
                warn!("Unknown message type: {}", request.message_type);
                Ok(())
            }
        }
    }

    async fn handle_register(
        &self,
        client_id: Uuid,
        request: ClientMessage,
        sender: &mpsc::UnboundedSender<Message>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        if let Some(username) = request.username {
            if let Some(public_key) = request.public_key {
                let public_key_bytes = base64::decode(&public_key)?;
                
                let client = ClientConnection {
                    user_id: client_id,
                    username: username.clone(),
                    public_key: public_key_bytes,
                    sender: sender.clone(),
                    is_authenticated: true,
                };
                
                self.clients.insert(client_id, client);
                
                let response = ServerMessage {
                    message_type: "register_success".to_string(),
                    user_id: Some(client_id.to_string()),
                    username: Some(username),
                    content: Some("Registration successful".to_string()),
                    timestamp: chrono::Utc::now(),
                    ..Default::default()
                };
                
                let response_json = serde_json::to_string(&response)?;
                sender.send(Message::Text(response_json))?;
                
                info!("Client {} registered successfully", client_id);
            }
        }
        Ok(())
    }

    async fn handle_authenticate(
        &self,
        client_id: Uuid,
        request: ClientMessage,
        sender: &mpsc::UnboundedSender<Message>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        // Implement authentication logic here
        // This could involve verifying signatures, checking credentials, etc.
        
        let response = ServerMessage {
            message_type: "auth_success".to_string(),
            user_id: Some(client_id.to_string()),
            content: Some("Authentication successful".to_string()),
            timestamp: chrono::Utc::now(),
            ..Default::default()
        };
        
        let response_json = serde_json::to_string(&response)?;
        sender.send(Message::Text(response_json))?;
        Ok(())
    }

    async fn handle_send_message(
        &self,
        client_id: Uuid,
        request: ClientMessage,
    ) -> Result<(), Box<dyn std::error::Error>> {
        if let Some(client) = self.clients.get(&client_id) {
            if !client.is_authenticated {
                return Ok(());
            }

            if let Some(room_id) = request.room_id {
                if let Some(encrypted_content) = request.encrypted_content {
                    let message = EncryptedMessage {
                        id: Uuid::new_v4(),
                        sender_id: client_id,
                        sender_username: client.username.clone(),
                        room_id: room_id.clone(),
                        encrypted_content,
                        timestamp: chrono::Utc::now(),
                        message_type: request.content_type.unwrap_or("text".to_string()),
                    };

                    // Store message (in production, use a proper database)
                    if let Some(mut room) = self.rooms.get_mut(&room_id) {
                        room.messages.push(message.clone());
                        
                        // Broadcast to room members
                        for member_id in &room.members {
                            if let Some(member) = self.clients.get(member_id) {
                                let response = ServerMessage {
                                    message_type: "new_message".to_string(),
                                    user_id: Some(client_id.to_string()),
                                    username: Some(client.username.clone()),
                                    room_id: Some(room_id.clone()),
                                    encrypted_content: Some(message.encrypted_content.clone()),
                                    timestamp: message.timestamp,
                                    ..Default::default()
                                };
                                
                                let response_json = serde_json::to_string(&response)?;
                                let _ = member.sender.send(Message::Text(response_json));
                            }
                        }
                    }
                }
            }
        }
        Ok(())
    }

    async fn handle_create_room(
        &self,
        client_id: Uuid,
        request: ClientMessage,
        sender: &mpsc::UnboundedSender<Message>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        if let Some(client) = self.clients.get(&client_id) {
            if !client.is_authenticated {
                return Ok(());
            }

            let room_id = request.room_id.unwrap_or_else(|| Uuid::new_v4().to_string());
            let room = Room {
                id: room_id.clone(),
                name: request.room_name.unwrap_or_else(|| format!("Room {}", room_id)),
                members: vec![client_id],
                messages: Vec::new(),
                created_at: chrono::Utc::now(),
            };

            self.rooms.insert(room_id.clone(), room);

            let response = ServerMessage {
                message_type: "room_created".to_string(),
                room_id: Some(room_id),
                content: Some("Room created successfully".to_string()),
                timestamp: chrono::Utc::now(),
                ..Default::default()
            };

            let response_json = serde_json::to_string(&response)?;
            sender.send(Message::Text(response_json))?;
        }
        Ok(())
    }

    async fn handle_join_room(
        &self,
        client_id: Uuid,
        request: ClientMessage,
        sender: &mpsc::UnboundedSender<Message>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        if let Some(client) = self.clients.get(&client_id) {
            if !client.is_authenticated {
                return Ok(());
            }

            if let Some(room_id) = request.room_id {
                if let Some(mut room) = self.rooms.get_mut(&room_id) {
                    if !room.members.contains(&client_id) {
                        room.members.push(client_id);
                    }

                    let response = ServerMessage {
                        message_type: "room_joined".to_string(),
                        room_id: Some(room_id),
                        content: Some("Joined room successfully".to_string()),
                        timestamp: chrono::Utc::now(),
                        ..Default::default()
                    };

                    let response_json = serde_json::to_string(&response)?;
                    sender.send(Message::Text(response_json))?;
                }
            }
        }
        Ok(())
    }

    async fn handle_get_messages(
        &self,
        client_id: Uuid,
        request: ClientMessage,
        sender: &mpsc::UnboundedSender<Message>,
    ) -> Result<(), Box<dyn std::error::Error>> {
        if let Some(client) = self.clients.get(&client_id) {
            if !client.is_authenticated {
                return Ok(());
            }

            if let Some(room_id) = request.room_id {
                if let Some(room) = self.rooms.get(&room_id) {
                    if room.members.contains(&client_id) {
                        let messages: Vec<ServerMessage> = room.messages.iter().map(|msg| {
                            ServerMessage {
                                message_type: "message".to_string(),
                                user_id: Some(msg.sender_id.to_string()),
                                username: Some(msg.sender_username.clone()),
                                room_id: Some(msg.room_id.clone()),
                                encrypted_content: Some(msg.encrypted_content.clone()),
                                timestamp: msg.timestamp,
                                ..Default::default()
                            }
                        }).collect();

                        for message in messages {
                            let response_json = serde_json::to_string(&message)?;
                            sender.send(Message::Text(response_json))?;
                        }
                    }
                }
            }
        }
        Ok(())
    }

    async fn cleanup_client(&self, client_id: Uuid) {
        // Remove client from rooms
        for mut room in self.rooms.iter_mut() {
            room.members.retain(|&id| id != client_id);
        }
        
        // Remove client connection
        self.clients.remove(&client_id);
        info!("Cleaned up client {}", client_id);
    }
}
```

## src/models.rs
```rust
use serde::{Deserialize, Serialize};
use uuid::Uuid;
use chrono::{DateTime, Utc};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClientMessage {
    pub message_type: String,
    pub username: Option<String>,
    pub room_id: Option<String>,
    pub room_name: Option<String>,
    pub content: Option<String>,
    pub encrypted_content: Option<String>,
    pub public_key: Option<String>,
    pub content_type: Option<String>,
    pub timestamp: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ServerMessage {
    pub message_type: String,
    pub user_id: Option<String>,
    pub username: Option<String>,
    pub room_id: Option<String>,
    pub content: Option<String>,
    pub encrypted_content: Option<String>,
    pub timestamp: DateTime<Utc>,
    pub error: Option<String>,
}

#[derive(Debug, Clone)]
pub struct Room {
    pub id: String,
    pub name: String,
    pub members: Vec<Uuid>,
    pub messages: Vec<EncryptedMessage>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone)]
pub struct EncryptedMessage {
    pub id: Uuid,
    pub sender_id: Uuid,
    pub sender_username: String,
    pub room_id: String,
    pub encrypted_content: String,
    pub timestamp: DateTime<Utc>,
    pub message_type: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KeyExchange {
    pub user_id: String,
    pub public_key: String,
    pub signature: String,
}
```

## src/crypto.rs
```rust
use aes_gcm::{Aes256Gcm, Key, Nonce, KeyInit};
use aes_gcm::aead::{Aead, AeadCore, OsRng};
use rand::RngCore;
use sha2::{Sha256, Digest};
use hmac::{Hmac, Mac};
use x25519_dalek::{EphemeralSecret, PublicKey as X25519PublicKey};
use ed25519_dalek::{Keypair, PublicKey as Ed25519PublicKey, SecretKey, Signature, Signer, Verifier};
use base64;

type HmacSha256 = Hmac<Sha256>;

#[derive(Clone)]
pub struct CryptoManager {
    server_signing_key: Keypair,
}

impl CryptoManager {
    pub fn new() -> Result<Self, Box<dyn std::error::Error>> {
        // Generate server signing keypair
        let mut csprng = OsRng;
        let secret_key = SecretKey::generate(&mut csprng);
        let public_key = Ed25519PublicKey::from(&secret_key);
        let server_signing_key = Keypair { secret: secret_key, public: public_key };

        Ok(Self {
            server_signing_key,
        })
    }

    /// Encrypt data using AES-256-GCM
    pub fn encrypt_data(&self, plaintext: &[u8], key: &[u8]) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let key = Key::<Aes256Gcm>::from_slice(key);
        let cipher = Aes256Gcm::new(key);
        
        let nonce = Aes256Gcm::generate_nonce(&mut OsRng);
        let ciphertext = cipher.encrypt(&nonce, plaintext)?;
        
        // Prepend nonce to ciphertext
        let mut result = Vec::new();
        result.extend_from_slice(&nonce);
        result.extend_from_slice(&ciphertext);
        
        Ok(result)
    }

    /// Decrypt data using AES-256-GCM
    pub fn decrypt_data(&self, ciphertext_with_nonce: &[u8], key: &[u8]) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        if ciphertext_with_nonce.len() < 12 {
            return Err("Invalid ciphertext length".into());
        }

        let (nonce, ciphertext) = ciphertext_with_nonce.split_at(12);
        let nonce = Nonce::from_slice(nonce);
        
        let key = Key::<Aes256Gcm>::from_slice(key);
        let cipher = Aes256Gcm::new(key);
        
        let plaintext = cipher.decrypt(nonce, ciphertext)?;
        Ok(plaintext)
    }

    /// Generate a random AES key
    pub fn generate_aes_key(&self) -> [u8; 32] {
        let mut key = [0u8; 32];
        OsRng.fill_bytes(&mut key);
        key
    }

    /// Derive key from shared secret using HKDF
    pub fn derive_key(&self, shared_secret: &[u8], salt: &[u8], info: &[u8]) -> [u8; 32] {
        let mut hasher = Sha256::new();
        hasher.update(shared_secret);
        hasher.update(salt);
        hasher.update(info);
        let hash = hasher.finalize();
        
        let mut key = [0u8; 32];
        key.copy_from_slice(&hash[..32]);
        key
    }

    /// Generate X25519 key exchange pair
    pub fn generate_x25519_keypair(&self) -> (EphemeralSecret, X25519PublicKey) {
        let secret = EphemeralSecret::new(OsRng);
        let public = X25519PublicKey::from(&secret);
        (secret, public)
    }

    /// Perform X25519 key exchange
    pub fn x25519_key_exchange(&self, secret: EphemeralSecret, their_public: &X25519PublicKey) -> [u8; 32] {
        let shared_secret = secret.diffie_hellman(their_public);
        *shared_secret.as_bytes()
    }

    /// Sign data with server key
    pub fn sign_data(&self, data: &[u8]) -> Signature {
        self.server_signing_key.sign(data)
    }

    /// Verify signature
    pub fn verify_signature(&self, data: &[u8], signature: &Signature, public_key: &Ed25519PublicKey) -> bool {
        public_key.verify(data, signature).is_ok()
    }

    /// Generate HMAC
    pub fn generate_hmac(&self, key: &[u8], data: &[u8]) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let mut mac = HmacSha256::new_from_slice(key)?;
        mac.update(data);
        Ok(mac.finalize().into_bytes().to_vec())
    }

    /// Verify HMAC
    pub fn verify_hmac(&self, key: &[u8], data: &[u8], expected_mac: &[u8]) -> Result<bool, Box<dyn std::error::Error>> {
        let mut mac = HmacSha256::new_from_slice(key)?;
        mac.update(data);
        Ok(mac.verify_slice(expected_mac).is_ok())
    }

    /// Hash password using Argon2
    pub fn hash_password(&self, password: &str, salt: &[u8]) -> Result<String, Box<dyn std::error::Error>> {
        let config = argon2::Config::default();
        let hash = argon2::hash_encoded(password.as_bytes(), salt, &config)?;
        Ok(hash)
    }

    /// Verify password hash
    pub fn verify_password(&self, password: &str, hash: &str) -> bool {
        argon2::verify_encoded(hash, password.as_bytes()).unwrap_or(false)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encrypt_decrypt() {
        let crypto = CryptoManager::new().unwrap();
        let key = crypto.generate_aes_key();
        let plaintext = b"Hello, encrypted world!";
        
        let encrypted = crypto.encrypt_data(plaintext, &key).unwrap();
        let decrypted = crypto.decrypt_data(&encrypted, &key).unwrap();
        
        assert_eq!(plaintext, &decrypted[..]);
    }

    #[test]
    fn test_key_exchange() {
        let crypto = CryptoManager::new().unwrap();
        
        let (alice_secret, alice_public) = crypto.generate_x25519_keypair();
        let (bob_secret, bob_public) = crypto.generate_x25519_keypair();
        
        let alice_shared = crypto.x25519_key_exchange(alice_secret, &bob_public);
        let bob_shared = crypto.x25519_key_exchange(bob_secret, &alice_public);
        
        assert_eq!(alice_shared, bob_shared);
    }
}
```

## src/handlers.rs
```rust
use std::collections::HashMap;
use uuid::Uuid;
use serde_json;
use tracing::{info, error, warn};

use crate::models::*;
use crate::crypto::CryptoManager;

pub struct MessageHandler {
    rate_limiter: HashMap<Uuid, RateLimiter>,
}

struct RateLimiter {
    last_message: chrono::DateTime<chrono::Utc>,
    message_count: u32,
}

impl MessageHandler {
    pub fn new() -> Self {
        Self {
            rate_limiter: HashMap::new(),
        }
    }

    /// Check if user is rate limited
    pub fn check_rate_limit(&mut self, user_id: Uuid) -> bool {
        let now = chrono::Utc::now();
        let limit = self.rate_limiter.entry(user_id).or_insert(RateLimiter {
            last_message: now,
            message_count: 0,
        });

        // Reset counter if more than 1 minute has passed
        if now.signed_duration_since(limit.last_message).num_seconds() > 60 {
            limit.message_count = 0;
            limit.last_message = now;
        }

        limit.message_count += 1;

        // Allow maximum 60 messages per minute
        if limit.message_count > 60 {
            warn!("Rate limit exceeded for user {}", user_id);
            return false;
        }

        true
    }

    /// Sanitize message content
    pub fn sanitize_content(&self, content: &str) -> String {
        // Basic content sanitization
        content
            .chars()
            .filter(|c| !c.is_control() || *c == '\n' || *c == '\r' || *c == '\t')
            .collect::<String>()
            .trim()
            .to_string()
    }

    /// Validate message structure
    pub fn validate_message(&self, message: &ClientMessage) -> Result<(), String> {
        if message.message_type.is_empty() {
            return Err("Message type cannot be empty".to_string());
        }

        if let Some(content) = &message.content {
            if content.len() > 10000 {  // 10KB limit
                return Err("Message content too large".to_string());
            }
        }

        if let Some(encrypted_content) = &message.encrypted_content {
            if encrypted_content.len() > 50000 {  // 50KB limit for encrypted content
                return Err("Encrypted content too large".to_string());
            }
        }

        Ok(())
    }

    /// Process file attachment (placeholder)
    pub fn process_attachment(&self, _attachment_data: &[u8]) -> Result<String, String> {
        // Implement file processing logic here
        // This could include virus scanning, file type validation, etc.
        Ok("attachment_processed".to_string())
    }
}

/// Security middleware for additional validation
pub struct SecurityMiddleware;

impl SecurityMiddleware {
    /// Validate client request for potential security issues
    pub fn validate_request(message: &ClientMessage) -> Result<(), String> {
        // Check for potential XSS/injection patterns
        if let Some(content) = &message.content {
            if content.contains("<script") || content.contains("javascript:") {
                return Err("Potentially malicious content detected".to_string());
            }
        }

        // Validate room ID format
        if let Some(room_id) = &message.room_id {
            if room_id.len() > 100 || !room_id.chars().all(|c| c.is_alphanumeric() || c == '-') {
                return Err("Invalid room ID format".to_string());
            }
        }

        Ok(())
    }

    /// Generate secure session token
    pub fn generate_session_token() -> String {
        use rand::RngCore;
        let mut token = [0u8; 32];
        rand::rngs::OsRng.fill_bytes(&mut token);
        base64::encode(token)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rate_limiter() {
        let mut handler = MessageHandler::new();
        let user_id = Uuid::new_v4();

        // Should allow first messages
        for _ in 0..60 {
            assert!(handler.check_rate_limit(user_id));
        }

        // Should block after limit
        assert!(!handler.check_rate_limit(user_id));
    }

    #[test]
    fn test_content_sanitization() {
        let handler = MessageHandler::new();
        let malicious_content = "Hello\x00World\x1F";
        let sanitized = handler.sanitize_content(malicious_content);
        assert_eq!(sanitized, "HelloWorld");
    }
}
```

## Usage Instructions

### Running the Server
```bash
# Install Rust and Cargo
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Clone and build
cargo build --release

# Run server
cargo run -- --bind 0.0.0.0:8080 --log-level info
```

### Client Connection Example (JavaScript)
```javascript
const ws = new WebSocket('ws://localhost:8080');

// Register user
ws.send(JSON.stringify({
    message_type: "register",
    username: "alice",
    public_key: "base64_encoded_public_key"
}));

// Send encrypted message
ws.send(JSON.stringify({
    message_type: "send_message",
    room_id: "room123",