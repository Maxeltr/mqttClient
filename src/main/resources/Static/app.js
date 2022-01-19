var stompClient = null;

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#disconnect").prop("disabled", !connected);

}

function connect() {
    var socket = new SockJS('/mqttClientDashboard');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        setConnected(true);
        console.log('Connected: ' + frame);
        stompClient.subscribe('/topic/replies', function (message) {
            showReplies(JSON.parse(message.body), message.headers.card);
        });
        stompClient.subscribe('/topic/data', function (message) {
            showMessages(JSON.parse(message.body), message.headers.card);
        });
    });
}

function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
    setConnected(false);
    console.log("Disconnected");
}

function createCommand(commandNumber) {
    stompClient.send("/app/createCommand", {}, JSON.stringify({'commandNumber': commandNumber}));
}

function showReplies(message, card) {
    console.log(card);

    if (message.timestamp === 'undefined') {
        console.log('message.timestamp is undefined');
        document.getElementById(card + '-timestamp').innerHTML === 'undefined';
    } else {
        var date = new Date(parseInt(message.timestamp, 10));
        var hours = date.getHours();
        var minutes = '0' + date.getMinutes();
        var seconds = '0' + date.getSeconds();
        document.getElementById(card + '-timestamp').innerHTML = hours + ':' + minutes.substr(-2) + ':' + seconds.substr(-2);
    }

    if (message.result === 'undefined') {
        console.log('message.result is undefined');
        document.getElementById(card + '-payload').innerHTML = '<p> message.result is undefined </p>';
        return;
    }

    if (message.result.toUpperCase() !== 'OK') {
        console.log('message.result is fail');
        document.getElementById(card + '-payload').innerHTML = '<p>' + 'Result is fail ' + message.result + '<br>' + message.payload + '</p>';
        return;
    }

    if (message.type !== 'undefined') {
        if (message.type.toUpperCase() === 'IMAGE/JPEG') {
            var image = new Image();
            image.src = 'data:image/jpeg;base64,' + message.payload;
            document.getElementById(card + '-payload').innerHTML = '<img src="' + image.src + '" class="img-fluid" alt="...">';
            var saveButton = document.getElementById(card + '-save');
            saveButton.setAttribute('href', image.src);
            saveButton.classList.remove("disabled");
        } else if (message.type.toUpperCase() === 'TEXT/PLAIN') {
            document.getElementById(card + '-payload').innerHTML = '<p>' + message.payload + '</p>';
        } else {
            console.log('message.type is ' + message.type);
            document.getElementById(card + '-payload').innerHTML = '<p>' + "Type is " + message.type + '<br>' + message.payload + '</p>';
        }
    } else {
        console.log('message.type is undefined');
        document.getElementById(card + '-payload').innerHTML = '<p>' + 'message.type is undefined' + '<br>' + message.payload + '</p>';
    }

//    document.getElementById(card + '-timestamp').innerHTML = message.timestamp;
//    if (message.result.toUpperCase() === 'OK') {
//        if (message.name.toUpperCase() === 'TAKESCREENSHOT') {
//            var image = new Image();
//            image.src = 'data:image/png;base64,' + message.payload;
//            document.getElementById(card + '-payload').innerHTML = '<img src="' + image.src + '" class="img-fluid" alt="...">';
//            var saveButton = document.getElementById(card + '-save');
//            saveButton.setAttribute('href', image.src);
//            saveButton.classList.remove("disabled");
//        } else {
//            document.getElementById(card + '-payload').innerHTML = '<p>' + message.result + '<br>' + message.payload + '</p>';
//        }
//    } else {
//        document.getElementById(card + '-payload').innerHTML = '<p>' + message.result + '<br>' + message.payload + '</p>';
//    }
}

function showMessages(message, card) {
//    console.log('message: ' + message.name);


}

$(function () {
    $("form").on('submit', function (e) {
        e.preventDefault();
    });
    $("#connect").click(function () {
        connect();
    });
    $("#disconnect").click(function () {
        disconnect();
    });
    $("#sendCommand ").click(function () {
        var arg = $(this).val()
        createCommand(arg);
    });


});



