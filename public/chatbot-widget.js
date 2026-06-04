/**
 * Widget Chatbot Flottant
 * Affiche une petite icône à droite de l'écran
 * Ouvre une fenêtre de chat quand on clique
 */

class ChatbotWidget {
  constructor() {
    this.isOpen = false;
    this.messages = [];
    this.init();
  }

  init() {
    this.createHTML();
    this.attachEventListeners();
  }

  createHTML() {
    const widgetHTML = `
      <div id="chatbot-widget" class="chatbot-widget">
        <!-- Icône flottante -->
        <div id="chatbot-toggle" class="chatbot-toggle" title="Aide client">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
          </svg>
        </div>

        <!-- Fenêtre de chat (masquée par défaut) -->
        <div id="chatbot-window" class="chatbot-window" style="display: none;">
          <div class="chatbot-header">
            <h3>Assistant Sarbi Rohek</h3>
            <button id="chatbot-close" class="chatbot-close" type="button">✕</button>
          </div>
          
          <div id="chatbot-messages" class="chatbot-messages"></div>
          
          <div class="chatbot-input-area">
            <input 
              id="chatbot-input" 
              type="text" 
              placeholder="Posez votre question..." 
              autocomplete="off"
            />
            <button id="chatbot-send" class="chatbot-send" type="button" title="Envoyer">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                <path d="M16.6915026,12.4744748 L3.50612381,13.2599618 C3.19218622,13.2599618 3.03521743,13.4170592 3.03521743,13.5741566 L1.15159189,20.0151496 C0.8376543,20.8006365 0.99,21.89 1.77946707,22.52 C2.41,22.99 3.50612381,23.1 4.13399899,22.8429026 L21.714504,14.0454487 C22.6563168,13.5741566 23.1272231,12.6315722 22.9702544,11.6889879 C22.9702544,11.6889879 22.9702544,11.5318905 22.9702544,11.4748839 L4.13399899,2.5 C3.34915502,2.20 2.40734225,2.31 1.77946707,2.99 C0.994623095,3.5 0.837654326,4.749347 1.15159189,5.53484626 L3.03521743,12.1002722 C3.03521743,12.25 3.34915502,12.4070974 3.50612381,12.4070974 L16.6915026,13.1925823 C16.6915026,13.1925823 17.1624089,13.1925823 17.1624089,12.8399193 L17.1624089,12.0544339 C17.1624089,11.8973365 17.1624089,11.4748839 16.6915026,11.4748839 C16.6915026,11.4748839 16.0636274,11.4748839 16.6915026,12.4744748 Z"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    `;

    const styleHTML = `
      <style>
        #chatbot-widget {
          position: fixed;
          bottom: 20px;
          right: 20px;
          font-family: "Poppins", sans-serif;
          z-index: 10000;
        }

        .chatbot-toggle {
          width: 60px;
          height: 60px;
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
          transition: all 0.3s ease;
          color: white;
        }

        .chatbot-toggle:hover {
          transform: scale(1.1);
          box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
        }

        .chatbot-toggle svg {
          width: 32px;
          height: 32px;
        }

        .chatbot-window {
          position: absolute;
          bottom: 90px;
          right: 0;
          width: 400px;
          height: 400px;
          background: white;
          border-radius: 12px;
          box-shadow: 0 5px 40px rgba(0, 0, 0, 0.16);
          display: flex;
          flex-direction: column;
          overflow: hidden;
          animation: slideUp 0.3s ease;
        }

        @keyframes slideUp {
          from {
            opacity: 0;
            transform: translateY(20px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }

        .chatbot-header {
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          color: white;
          padding: 16px;
          display: flex;
          justify-content: space-between;
          align-items: center;
          border-bottom: 1px solid rgba(0, 0, 0, 0.1);
        }

        .chatbot-header h3 {
          margin: 0;
          font-size: 16px;
          font-weight: 600;
        }

        .chatbot-close {
          background: none;
          border: none;
          color: white;
          font-size: 24px;
          cursor: pointer;
          padding: 0;
          width: 32px;
          height: 32px;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: transform 0.2s;
        }

        .chatbot-close:hover {
          transform: rotate(90deg);
        }

        .chatbot-messages {
          flex: 1;
          overflow-y: auto;
          padding: 16px;
          display: flex;
          flex-direction: column;
          gap: 12px;
          background: #f8f9fa;
        }

        .chatbot-message {
          display: flex;
          gap: 8px;
          animation: fadeIn 0.3s ease;
        }

        @keyframes fadeIn {
          from {
            opacity: 0;
            transform: translateY(10px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }

        .chatbot-message.user {
          justify-content: flex-end;
        }

        .chatbot-message-avatar {
          width: 32px;
          height: 32px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 16px;
          flex-shrink: 0;
        }

        .chatbot-message.bot .chatbot-message-avatar {
          background: #667eea;
          color: white;
        }

        .chatbot-message.user .chatbot-message-avatar {
          background: #e2e8f0;
          color: #333;
        }

        .chatbot-message-content {
          max-width: 70%;
          padding: 10px 14px;
          border-radius: 12px;
          word-wrap: break-word;
          line-height: 1.4;
          font-size: 14px;
        }

        .chatbot-message.bot .chatbot-message-content {
          background: white;
          color: #333;
          border: 1px solid #e0e0e0;
        }

        .chatbot-message.user .chatbot-message-content {
          background: #667eea;
          color: white;
        }

        .chatbot-input-area {
          padding: 12px;
          border-top: 1px solid #e0e0e0;
          display: flex;
          gap: 8px;
          background: white;
        }

        #chatbot-input {
          flex: 1;
          border: 1px solid #e0e0e0;
          border-radius: 8px;
          padding: 10px 12px;
          font-family: "Poppins", sans-serif;
          font-size: 14px;
          outline: none;
          transition: border-color 0.2s;
        }

        #chatbot-input:focus {
          border-color: #667eea;
        }

        .chatbot-send {
          width: 40px;
          height: 40px;
          border: none;
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          color: white;
          border-radius: 8px;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: transform 0.2s;
        }

        .chatbot-send:hover {
          transform: scale(1.05);
        }

        .chatbot-send:active {
          transform: scale(0.95);
        }

        .chatbot-send svg {
          width: 20px;
          height: 20px;
        }

        .chatbot-send:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }

        .chatbot-typing {
          display: flex;
          gap: 4px;
          align-items: center;
        }

        .chatbot-typing span {
          width: 8px;
          height: 8px;
          background: #999;
          border-radius: 50%;
          animation: typing 1.4s infinite;
        }

        .chatbot-typing span:nth-child(2) {
          animation-delay: 0.2s;
        }

        .chatbot-typing span:nth-child(3) {
          animation-delay: 0.4s;
        }

        @keyframes typing {
          0%, 60%, 100% {
            opacity: 0.3;
          }
          30% {
            opacity: 1;
          }
        }

        /* Responsive */
        @media (max-width: 480px) {
          .chatbot-window {
            width: calc(100vw - 20px);
            height: 70vh;
            bottom: 10px;
            right: 10px;
            left: 10px;
          }

          .chatbot-message-content {
            max-width: 85%;
          }
        }
      </style>
    `;

    // Injecter le style
    const styleElement = document.createElement('div');
    styleElement.innerHTML = styleHTML;
    document.head.appendChild(styleElement.querySelector('style'));

    // Injecter le widget HTML
    const widgetElement = document.createElement('div');
    widgetElement.innerHTML = widgetHTML;
    document.body.appendChild(widgetElement);
  }

  attachEventListeners() {
    const toggle = document.getElementById('chatbot-toggle');
    const closeBtn = document.getElementById('chatbot-close');
    const sendBtn = document.getElementById('chatbot-send');
    const input = document.getElementById('chatbot-input');

    toggle.addEventListener('click', () => this.toggleWindow());
    closeBtn.addEventListener('click', () => this.closeWindow());
    sendBtn.addEventListener('click', () => this.sendMessage());
    input.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') this.sendMessage();
    });
  }

  toggleWindow() {
    const window = document.getElementById('chatbot-window');
    if (this.isOpen) {
      this.closeWindow();
    } else {
      window.style.display = 'flex';
      this.isOpen = true;
      document.getElementById('chatbot-input').focus();
      // Afficher un message de bienvenue si vide
      if (this.messages.length === 0) {
        this.addBotMessage('Bonjour! Je suis l\'assistant Sarbi Rohek. Comment puis-je vous aider aujourd\'hui?');
      }
    }
  }

  closeWindow() {
    const window = document.getElementById('chatbot-window');
    window.style.display = 'none';
    this.isOpen = false;
  }

  sendMessage() {
    const input = document.getElementById('chatbot-input');
    const message = input.value.trim();

    if (message.length === 0) return;

    // Ajouter le message de l'utilisateur
    this.addUserMessage(message);
    input.value = '';

    // Afficher l'indicateur de typing
    const typingId = this.addBotTyping();

    // Envoyer au serveur
    fetch('/sarbi_rohek/api/chatbot', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ message: message })
    })
      .then(response => response.json())
      .then(data => {
        // Supprimer l'indicateur de typing
        this.removeTyping(typingId);

        if (data.success) {
          this.addBotMessage(data.message);
        } else {
          this.addBotMessage('Erreur: ' + (data.error || 'Une erreur est survenue.'));
        }
      })
      .catch(error => {
        this.removeTyping(typingId);
        this.addBotMessage('Impossible de communiquer avec le serveur. Veuillez réessayer.');
        console.error('Erreur:', error);
      });
  }

  addUserMessage(text) {
    const messagesDiv = document.getElementById('chatbot-messages');
    const messageEl = document.createElement('div');
    messageEl.className = 'chatbot-message user';
    messageEl.innerHTML = `
      <div class="chatbot-message-content">${this.escapeHtml(text)}</div>
      <div class="chatbot-message-avatar">👤</div>
    `;
    messagesDiv.appendChild(messageEl);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
    this.messages.push({ role: 'user', content: text });
  }

  addBotMessage(text) {
    const messagesDiv = document.getElementById('chatbot-messages');
    const messageEl = document.createElement('div');
    messageEl.className = 'chatbot-message bot';
    messageEl.innerHTML = `
      <div class="chatbot-message-avatar">🤖</div>
      <div class="chatbot-message-content">${this.escapeHtml(text)}</div>
    `;
    messagesDiv.appendChild(messageEl);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
    this.messages.push({ role: 'bot', content: text });
  }

  addBotTyping() {
    const messagesDiv = document.getElementById('chatbot-messages');
    const messageEl = document.createElement('div');
    messageEl.className = 'chatbot-message bot';
    messageEl.id = 'typing-indicator';
    messageEl.innerHTML = `
      <div class="chatbot-message-avatar">🤖</div>
      <div class="chatbot-message-content">
        <div class="chatbot-typing">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </div>
    `;
    messagesDiv.appendChild(messageEl);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
    return 'typing-indicator';
  }

  removeTyping(id) {
    const element = document.getElementById(id);
    if (element) {
      element.remove();
    }
  }

  escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }
}

// Initialiser le widget quand le DOM est prêt
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => {
    new ChatbotWidget();
  });
} else {
  new ChatbotWidget();
}
