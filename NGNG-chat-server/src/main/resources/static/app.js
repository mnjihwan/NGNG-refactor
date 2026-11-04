//const fileInput = document.getElementById("file");
//const handleFiles = () => {
//    const selectedFiles = [...fileInput.files];
//    console.log(selectedFiles);
//}
//fileInput.addEventListener("change",handleFiles);


const stompClient = new StompJs.Client({
    brokerURL: 'ws://localhost:8081/chat-server' // webSocketConfig의 addEndpoint
});

stompClient.onConnect = (frame) => {
    setConnected(true);
    console.log('Connected: ' + frame);
    // 3부분에 productId 동적으로 넣어주기
    stompClient.subscribe('/product/'+'3', (greeting) => {
        const data = JSON.parse(greeting.body).body;
        console.log('data', data)
        showGreeting(`${data.userNickname} (${data.userName}) : ${data.message} - ${data.createdAt}`);
    });
};

stompClient.onWebSocketError = (error) => {
    console.error('Error with websocket', error);
};

stompClient.onStompError = (frame) => {
    console.error('Broker reported error: ' + frame.headers['message']);
    console.error('Additional details: ' + frame.body);
};

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#disconnect").prop("disabled", !connected);
    if (connected) {
        $("#conversation").show();
    }
    else {
        $("#conversation").hide();
    }
    $("#greetings").html("");
}

function connect() {
    stompClient.activate();
}

function disconnect() {
    stompClient.deactivate();
    setConnected(false);
    console.log("Disconnected");
}

function sendName() {
    stompClient.publish({
    // 3부분에 동적으로 productId 넣어주기
        destination: "/public-chat/"+"3",
        body: JSON.stringify({'message': $("#name").val(),'userId': 1})
    });
}

function showGreeting(message) {
    console.log(message);
    $("#greetings").append("<tr><td>" + message + "</td></tr>");
}

$(function () {
    $("form").on('submit', (e) => e.preventDefault());
    $( "#connect" ).click(() => connect());
    $( "#disconnect" ).click(() => disconnect());
    $( "#send" ).click(() => sendName());
});

